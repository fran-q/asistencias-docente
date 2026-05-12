# ADR-0004: Defensa en profundidad multi-tenant en queries con JOIN

**Estado**: Aceptada
**Fecha**: 2026-05-06
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

En el Sprint 1 establecimos que el aislamiento multi-tenant se basa en tres capas defensivas (ADR-0002):

1. Filtro Hibernate `@Filter("tenant")` aplicado a entidades anotadas, activado por request via `TenantFilterAspect`.
2. Validación explícita en services (`ensureMismoTenant`, `obtener...Validado`).
3. Restricciones de FK + índices compuestos `(institucion_id, ...)` en la BD.

En el Sprint 2 Fase D descubrimos un **agujero importante** en la capa 1: **el filtro Hibernate NO se propaga automáticamente a entidades JOINeadas en JPQL**.

### El bug

`Comision` no es tenant-scoped directamente (no tiene `@Filter`); el tenant lo determina la `Materia` padre. La query para listar comisiones del tenant era:

```java
@Query("SELECT c FROM Comision c JOIN c.materia m ORDER BY ...")
List<Comision> findAllDelTenant();
```

Esperábamos que el `@Filter("tenant")` sobre `Materia` aplicara automáticamente a la JOIN. **No fue así**: el SUPERADMIN UTF veía las comisiones de CENT35 al pegarle a `/comisiones`.

### La razón

El filtro Hibernate se aplica:

- ✅ A retrievals directos de la entidad filtrada (`materiaRepository.findAll()`).
- ✅ A las colecciones `@OneToMany`/`@ManyToMany` de la entidad filtrada.
- ❌ **NO** se propaga automáticamente a JOINs en JPQL donde el SELECT es de otra entidad.

Esto está documentado pero es contraintuitivo. La superficie de bug es alta porque las queries con JOIN son comunes (reportes, vistas combinadas).

## Decisión

**Toda query JPQL `@Query` que JOINee a una entidad tenant-scoped debe filtrar explícitamente por `institucionId`.**

El parámetro `tenantId` se pasa desde el service usando `TenantContext.getRequired()`.

### Patrón estándar

```java
// Repository
@Query("""
    SELECT c FROM Comision c
    JOIN c.materia m
    WHERE m.institucionId = :tenantId
      AND ... otros criterios ...
    """)
List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);
```

```java
// Service
@Transactional(readOnly = true)
public List<Comision> listar() {
    Long tenantId = TenantContext.getRequired();
    return comisionRepository.findAllDelTenant(tenantId);
}
```

### Cuándo aplica

| Tipo de query | Necesita `WHERE m.institucionId = :tenantId` ? |
|---|---|
| `findAll()` sobre entidad con `@Filter` (Carrera, Materia, Usuario, ...) | **No** — el filtro aplica automáticamente |
| Derived queries de Spring Data (`findByMateriaId...`) | **No** — Spring genera JPQL sobre la entidad raíz solamente |
| `@Query` JPQL con JOIN a entidad tenant-scoped | **SÍ** |
| `nativeQuery=true` | **SÍ** — ni siquiera Hibernate aplica nada |
| `findById` sobre entidad tenant-scoped | **No** (Spring usa filter), pero **igual** se valida en service por defensa en profundidad |

### Defensa en profundidad — sigue siendo necesaria

Aunque agreguemos el WHERE explícito en las queries, los services siguen validando explícitamente:

```java
private Comision obtenerComisionValidada(Long comisionId, Long tenantId) {
    Comision c = comisionRepository.findById(comisionId)
        .orElseThrow(() -> new IllegalArgumentException("La comisión no existe."));
    if (c.getMateria() == null || !tenantId.equals(c.getMateria().getInstitucionId())) {
        throw new IllegalArgumentException("La comisión no existe.");
    }
    return c;
}
```

Esta validación atrapa casos donde el bug del filtro no aplica (ej: `findById`, métodos derivados de Spring Data sobre entidades cuyo tenant es transitivo).

## Aplicación en Sprint 2

Implementado en:

- `ComisionRepository.findAllDelTenant(tenantId)` — Fase D.
- `ComisionRepository.findActivasDelTenant(tenantId)` — Fase E.
- `HorarioRepository.findActivosPorCarrera(carreraId, tenantId)` — Fase E.

Cualquier `@Query` JPQL nueva en Sprint 3+ con JOIN a entidad tenant-scoped debe seguir el patrón.

## Consecuencias

**Positivas:**

- Cero filtraciones multi-tenant en queries con JOIN.
- El bug es imposible de "olvidar" si se sigue el patrón.
- Más explícito y legible: la intención de filtrado por tenant queda en el código.

**Negativas:**

- Pequeño overhead de boilerplate: cada query lleva `@Param("tenantId")`.
- Posible duplicación: el filtro Hibernate Y el WHERE explícito hacen lo mismo cuando la entidad raíz es tenant-scoped (aunque dos filtros idénticos no causan problema funcional).
- Falla silenciosa si se olvida — no hay aviso del compilador. Mitigación: tests de aislamiento manual al final de cada CRUD que JOINee (es lo que llevó a detectar TD-003), y a futuro tests automatizados con Testcontainers cuando entre integración real.

## Alternativa descartada

Denormalizar `institucion_id` en TODAS las tablas (no solo las tenant-scoped naturales) — agregarlo a `comisiones`, `horarios`, etc. Permite usar `@Filter` sin JOIN.

Se descartó por:
- Duplicación de datos.
- Riesgo de inconsistencia: si se cambia la materia padre, hay que actualizar denormalización en cascada.
- No agrega valor para nuestras consultas: el JOIN ya es necesario para mostrar la jerarquía completa.

## Referencias

- [TD-003](../TECH_DEBT.md) — Reporte del bug y mitigación detallada.
- [ADR-0002](./0002-multi-tenant-discriminator.md) — Diseño original multi-tenant.
- Hibernate ORM docs §3.3.2 (filters): aclaración sobre el scope del filtro.
- Sprint 2 Fase D commit `feat(academico): CRUD de Comisiones + fix fuga multi-tenant en JOINs`.
