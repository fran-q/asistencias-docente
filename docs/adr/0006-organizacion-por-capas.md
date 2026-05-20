# ADR-0006: Reorganización a package-by-layer

**Estado**: Aceptada
**Fecha**: 2026-05-19
**Decisor**: Francisco Quiroga (fran-q)
**Reemplaza a**: [ADR-0001](./0001-monolito-modular.md)

## Contexto

[ADR-0001](./0001-monolito-modular.md) había establecido una organización **package-by-feature** (cada dominio — `docente`, `academico`, `usuario`, etc. — con sub-paquetes `domain`, `application`, `infrastructure`, `web`). Esa decisión se mantuvo durante los Sprints 0, 1, 2 y 3.

Al cierre del Sprint 3, el docente de Prácticas Profesionalizantes III pidió **migrar la organización a package-by-layer**: una estructura plana donde todas las clases del mismo tipo viven juntas (`controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`). Es la organización clásica que se enseña en muchos cursos de Spring y que el evaluador busca reconocer al abrir el proyecto.

Aunque package-by-feature sigue siendo el estándar moderno (DDD, Hexagonal, Clean Architecture, guías oficiales de Spring), **el pedido es vinculante para la evaluación académica del proyecto**. La decisión técnica se subordina al criterio del evaluador.

## Decisión

Se migra el código a una estructura **package-by-layer** plana:

```
edu.cent35.asistencias/
├── AsistenciasApplication.java
├── controller/    (todos los @Controller)
├── service/       (todos los @Service + utilitarios de service)
├── repository/    (todos los Repository de Spring Data)
├── model/         (todas las @Entity, enums y BaseTenantEntity)
├── dto/           (todos los DTOs)
└── config/        (security, multi-tenancy, JpaConfig, WebMvcConfig)
```

### Reglas de la nueva estructura

1. **Paquetes planos**: ningún sub-paquete por feature. Si en el futuro un paquete crece demasiado, se evalúa subdividir.
2. **Nombre `model`**: el paquete que contiene `@Entity` se llama `model` (no `entity` ni `domain`). Decisión a pedido del evaluador.
3. **Enums junto a las entidades**: `RolCodigo`, `DiaSemana`, `EstadoConsentimiento`, `MetodoConsentimiento` viven en `model/` con las @Entity.
4. **`BaseTenantEntity` en `model/`**: la superclase `@MappedSuperclass` no es config; es parte del modelo.
5. **`TextoConsentimiento` en `service/`**: clase utilitaria con la constante de versión y el cuerpo legal — pertenece a la capa de servicio porque su consumidor es el `ConsentimientoBiometricoService`.
6. **`@FilterDef("tenant")`**: declarado en `model/package-info.java` (antes vivía en `shared/multitenant/package-info.java`).
7. **`shared/` desaparece**: su contenido se reparte entre `config/` (security, multi-tenancy) y `controller/HomeController`.

## Migración ejecutada

47 archivos `.java` movidos con `git mv` (preserva historial). Refactor masivo de `package` declarations e `import` statements con `sed`. 38 `package-info.java` viejos eliminados; uno nuevo creado en `model/`. Build verde con `./gradlew build` (66 tests pasan).

Imports wildcard (`import edu.cent35.asistencias.model.*`, `dto.*`, `repository.*`) agregados en cada capa para preservar las referencias que antes funcionaban por convivir en el mismo paquete. **Después de migrar conviene pasar "Optimize imports" desde IntelliJ** para reemplazarlos por imports específicos donde corresponda.

## Consecuencias

**Positivas:**
- Estructura inmediatamente reconocible para quien viene de tutoriales o cursos de Spring estándar.
- Cumple el pedido explícito del docente — preserva la nota.
- Una sola carpeta por capa: menos navegación entre niveles de árbol.

**Negativas:**
- Pérdida de cohesión por dominio: para entender "qué hace el Docente" hay que mirar 5 carpetas distintas.
- Mayor acoplamiento conceptual entre dominios (no físico, pero menos visible en la estructura).
- Si en el futuro se quisiera extraer un módulo a microservicio, hay que rearmar la frontera manualmente.
- Las reglas de acoplamiento de ADR-0001 (un dominio no toca entidades de otro) ya no se pueden expresar en la estructura — ahora son responsabilidad del desarrollador.
- Los `package-info.java` originales con documentación por dominio se perdieron (38 archivos). Si el equipo crece, conviene reintroducir alguna forma de documentar capas.

## Notas para el futuro

- Si se quisiera revertir a package-by-feature, el `git log` preserva los movimientos (`git log --follow` por archivo). El esfuerzo de migración inversa es similar al de esta migración: ~1 día.
- El paquete `model/` se va a engordar a medida que crezcan las features. Cuando llegue Sprint 4 (reconocimiento facial), van a entrar al menos `ModeloFacial`, `IntentoReconocimiento`, `ResultadoMatch`. Llegado a unas 20 entidades, evaluar sub-paquetes opcionales (`model/biometria/`, `model/asistencia/`).
- `dto/` también va a crecer. Misma evaluación.

## Referencias

- ADR-0001 (reemplazada): decisión original de package-by-feature.
- Sprint 3.5: migración ejecutada en el commit que acompaña este ADR.
- Discusión cliente-evaluador: pedido del docente de Prácticas Profesionalizantes III.
- "Package by Layer vs Package by Feature" — discusión clásica de Spring/Java; ambas son válidas, el criterio depende del contexto.
