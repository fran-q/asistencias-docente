# ADR-0001: Monolito Modular organizado por dominio

**Estado**: Aceptada
**Fecha**: 2026-04-26
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

El sistema necesita una arquitectura que sea:
- Sencilla de desarrollar y desplegar para un equipo de una sola persona.
- Lo suficientemente modular como para permitir extraer componentes (por ejemplo, el módulo de reconocimiento facial) a microservicios en el futuro si la carga lo requiere.
- Apropiada para una primera entrega académica de aproximadamente 60 horas de esfuerzo, con despliegue local.

Las alternativas evaluadas fueron:

1. **Microservicios desde el día 1**: descartado por overhead operativo (varios procesos, comunicación entre servicios, observabilidad distribuida) sin justificación funcional.
2. **Arquitectura hexagonal pura (puertos y adaptadores)**: descartado por overhead conceptual y de boilerplate excesivo para el alcance del proyecto.
3. **Monolito clásico organizado por capa técnica** (controllers/, services/, repositories/): descartado porque mezcla código de distintos dominios y dificulta entender el negocio.
4. **Monolito modular organizado por dominio**: elegido.

## Decisión

Se adopta una arquitectura de **Monolito Modular** organizado **por dominio** (feature-based packaging).

Cada módulo de dominio (institucion, usuario, docente, biometria, academico, reconocimiento, asistencia, reporte, auditoria) mantiene una estructura interna en cuatro capas:
- `domain/`: entidades JPA, value objects, enums.
- `application/`: services con la lógica de negocio.
- `infrastructure/`: repositorios JPA, integraciones externas.
- `web/`: controladores Spring MVC, DTOs, formularios.

El paquete `shared/` agrupa código transversal: configuración, seguridad, multi-tenancy, auditoría, manejo de excepciones.

## Reglas de acoplamiento

1. Un módulo de dominio NO accede a entidades JPA de otro módulo: solo a sus services públicos.
2. Las entidades JPA no salen de la capa `application`: en `web` se usan DTOs.
3. Las dependencias cruzadas pasan por `shared/`.

## Consecuencias

**Positivas:**
- Estructura legible: al abrir el proyecto se entiende el dominio sin leer código.
- Refactor a microservicios futuro es viable módulo a módulo.
- Separación de responsabilidades clara dentro de cada módulo.

**Negativas:**
- Más carpetas vacías al inicio (mitigado con `package-info.java` documentando cada paquete).
- Requiere disciplina del desarrollador para no romper las reglas de acoplamiento.

## Referencias

- Sección 5 de la Guía del Proyecto (`docs/1. Guia Proyecto Sistema Asistencias.docx`).
- "Modular Monoliths" - Simon Brown.
