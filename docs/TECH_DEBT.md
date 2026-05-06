# Deuda técnica conocida

Este archivo registra las decisiones técnicas que conscientemente postergamos. No son bugs — son trade-offs documentados para retomar cuando tenga sentido.

## TD-001: Nombres explícitos de PRIMARY KEY ignorados por MariaDB

**Detectado**: Sprint 0 (V001__init.sql)
**Severidad**: Baja (cosmético)
**Estado**: Aceptado — no se corrige

### Síntoma

Al aplicar la migración V001, Flyway loguea ~15 warnings de la forma:
```
DB: Name 'pk_instituciones' ignored for PRIMARY key. (SQL State: 42000 - Error Code: 1280)
```

### Causa

En `V001__init.sql` declaramos las claves primarias con sintaxis nombrada:
```sql
CONSTRAINT pk_instituciones PRIMARY KEY (id)
```

MariaDB acepta la PK pero **descarta el nombre** — todas las primary keys en MariaDB se llaman internamente `PRIMARY` por restricción del motor (no es configurable). El estándar SQL permite nombres custom, pero MariaDB/MySQL no los honran para PKs (sí para FKs y UNIQUE).

### Por qué no se corrige

1. **Las PKs funcionan correctamente** — los warnings son sólo informativos.
2. **V001 ya está aplicada** y commiteada. Modificarla rompería el tracking de Flyway.
3. Una migración nueva V00X que renombre/recree las PKs traería más riesgo (lock de tablas, recálculo de índices) que beneficio (silenciar logs).
4. Si en el futuro se migrara a PostgreSQL u otro motor que sí honre los nombres, los CONSTRAINT pk_xxx ya están listos.

### Mitigación

Los warnings aparecen una sola vez por migración. En arranques posteriores Flyway no re-aplica V001, así que los logs limpios.

---

## TD-002: Driver MySQL en lugar de MariaDB nativo (RESUELTO en Sprint 1)

**Detectado**: Sprint 0
**Severidad**: Baja (cosmético + sub-óptimo)
**Estado**: Resuelto — ver commit de Sprint 1

### Síntoma original

```
HHH000511: The 5.5.5 version for [org.hibernate.dialect.MariaDBDialect]
is no longer supported, hence certain features may not work properly.
```

Hibernate detecta MariaDB como "5.5.5" porque MariaDB devuelve esa versión legacy en el handshake JDBC para mantener compatibilidad con clientes MySQL antiguos.

### Solución aplicada

Cambio en `build.gradle`:
```diff
- runtimeOnly 'com.mysql:mysql-connector-j'
+ runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'
```

Y en `application-local.properties`:
```diff
- spring.datasource.url=jdbc:mysql://...
+ spring.datasource.url=jdbc:mariadb://...
- spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
+ spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
```

El driver nativo de MariaDB hace el handshake correctamente y reporta la versión real (10.4.32), por lo que Hibernate elige features modernos sin warnings.

---

## TD-003: El filtro Hibernate "tenant" NO se propaga a JOINs en JPQL

**Detectado**: Sprint 2 Fase D (Comisiones).
**Severidad**: **Alta** — fuga multi-tenant si se ignora.
**Estado**: Aceptado como invariante. Mitigado con regla de codigo.

### Síntoma

Una query JPQL del tipo:

```java
@Query("SELECT c FROM Comision c JOIN c.materia m ORDER BY ...")
List<Comision> findAllDelTenant();
```

devolvía Comisiones de **todos los tenants**, aunque `Materia` tiene `@Filter(name="tenant")` y el `TenantFilterAspect` activa el filtro por request.

Resultado: el SUPERADMIN UTF veía las comisiones de CENT35 al pegarle a `/comisiones`.

### Causa

El filtro Hibernate (`@Filter`) **se aplica a la entidad raíz del SELECT** y a sus colecciones (`@OneToMany`/`@ManyToMany`), pero **no se propaga automáticamente a entidades JOINeadas** desde JPQL. Esto está documentado pero es contraintuitivo.

En particular: `Comision` no es tenant-scoped (no tiene `@Filter`); el JOIN a `Materia` (que sí tiene `@Filter`) no fuerza el WHERE `materia.institucion_id = :tenantId` automáticamente cuando el SELECT es de Comision.

### Mitigación aplicada

**Regla**: cualquier `@Query` JPQL que JOINee a una entidad tenant-scoped (Materia, Carrera, Usuario, Docente, etc.) **debe filtrar explícitamente** por `institucionId`:

```java
@Query("""
    SELECT c FROM Comision c
    JOIN c.materia m
    WHERE m.institucionId = :tenantId
    ORDER BY ...
    """)
List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);
```

El service pasa `TenantContext.getRequired()` como argumento.

### Cuándo aplica esta regla

- ✅ Aplica: queries `@Query` JPQL/HQL con JOIN a entidad filtrada.
- ✅ Aplica: queries nativas (`nativeQuery=true`).
- ❌ No aplica: derived queries de Spring Data (ej: `findByMateriaIdAndCodigo`) — generan JPQL sobre la entidad raíz solamente, sin JOIN explícito a entidad filtrada.
- ❌ No aplica: `findAll()` sobre entidad que SÍ tiene `@Filter` — el filtro funciona normal.

### Lecciones para Sprint 3+

Cuando agreguemos consultas con JOIN a entidades de docente, asistencia, etc., **siempre incluir el `WHERE m.institucionId = :tenantId`** explícitamente. La defensa en profundidad sigue siendo:

1. Filtro Hibernate en la entidad raíz (cuando la entidad es tenant-scoped).
2. WHERE explícito en cualquier JOIN.
3. Validación en service (verificar que el resultado pertenece al tenant).
4. ID secuenciales (no leakeables) + camuflado como "no encontrado" si hay cross-tenant.

Tests de aislamiento al final de cada CRUD que JOINee. **Esto se descubrió manualmente en Fase D — no automatizado**. Pendiente: agregar test de integración con dos tenants.
