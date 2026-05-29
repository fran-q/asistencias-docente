# Sistema de Asistencias Digital con Reconocimiento Facial

Sistema web multi-tenant para automatizar el registro de asistencia docente en universidades e institutos terciarios mediante reconocimiento facial.

> Proyecto académico - Prácticas Profesionalizantes III - CENT35 - Tierra del Fuego, Argentina.

## Estado

**En desarrollo** — Sprint 5 cerrado (asistencia automática end-to-end: pase por reconocimiento facial, listado con AUSENTES calculadas, carga manual con motivo del catálogo, justificación de ausencias). Próximo: Sprint 6 (reportes, UML, video demo, cierre del proyecto). Primera entrega prevista: junio 2026.

## Stack

- **Java 21** + **Spring Boot 3.5.x**
- **Gradle** (Groovy DSL)
- **MySQL / MariaDB 10.4+** (vía XAMPP en desarrollo local)
- **Spring Data JPA** + **Hibernate** + **Flyway** (migraciones de esquema)
- **Spring Security** (autenticación con sesión HTTP)
- **Thymeleaf** (renderizado server-side)
- **JavaCV / OpenCV** (reconocimiento facial - Sprint 4)

## Características

- Multi-tenant con aislamiento total entre instituciones (200 a 400 docentes por institución).
- Reconocimiento facial automático con embeddings cifrados (sin almacenar fotografías).
- Carga manual de asistencia como fallback.
- Auditoría completa de acciones administrativas.
- Cumplimiento de la Ley 25.326 y Resolución AAIP 255/2022 sobre datos biométricos.

## Estructura del proyecto

Organización **package-by-layer** (ver [ADR-0006](docs/adr/0006-organizacion-por-capas.md)).

```
asistencias/
├── docs/                              ← documentación versionada
│   ├── adr/                           (decisiones arquitectónicas)
│   ├── uml/                           (diagramas .puml + .png)
│   └── legal/                         (textos de consentimiento)
├── src/
│   ├── main/
│   │   ├── java/edu/cent35/asistencias/
│   │   │   ├── AsistenciasApplication.java
│   │   │   ├── controller/            (Spring @Controller)
│   │   │   ├── service/               (lógica de negocio @Service)
│   │   │   ├── repository/            (Spring Data JPA)
│   │   │   ├── model/                 (@Entity, enums, BaseTenantEntity)
│   │   │   ├── dto/                   (objetos de transporte UI ↔ controller)
│   │   │   └── config/                (security, multi-tenancy, web/JPA config)
│   │   └── resources/
│   │       ├── db/migration/          (scripts Flyway V001__init.sql, ...)
│   │       ├── static/                (CSS / JS)
│   │       └── templates/             (vistas Thymeleaf)
│   └── test/
├── build.gradle
└── README.md
```

## Roadmap (primera entrega)

| Sprint | Período | Entregable | Estado |
|---|---|---|---|
| **S0** | 24-abr a 30-abr | Setup: repo + Spring Boot + MariaDB + Flyway + login dummy | ✅ |
| **S1** | 01-may a 04-may | Multi-tenancy + autenticación real + CRUDs (Mi Institución y Usuarios) | ✅ |
| **S2** | 05-may a 06-may | CRUD académico (carreras, materias, comisiones, horarios) + grilla semanal | ✅ |
| **S3** | 07-may a 28-may | CRUD docentes + consentimiento biométrico | ✅ |
| **S4** | 29-may a 11-jun | PoC reconocimiento facial con OpenCV | ✅ |
| **S5** | 12-jun a 18-jun | MVP de asistencia automática end-to-end | ✅ |
| **S6** | 19-jun a 24-jun | Cierre: diagramas UML, manuales, video demo | ⏳ |

Detalle completo en `docs/1. Guia Proyecto Sistema Asistencias.docx`.

## Documentación

- 📄 [Guía completa del proyecto](docs/1.%20Guia%20Proyecto%20Sistema%20Asistencias.docx)
- 🗂️ [Diagrama de Base de Datos](docs/2.%20Diagrama%20BD%20Sistema%20Asistencias.pdf)
- 📋 [Documento de Requerimientos](docs/3.%20Requerimientos%20Sistema%20Asistencias.docx)
- 🏛️ [Decisiones arquitectónicas (ADR)](docs/adr/)

## Marco legal

El sistema procesa datos biométricos sensibles. Cumple con:
- **Ley Nacional N° 25.326** de Protección de Datos Personales.
- **Resolución AAIP N° 255/2022** sobre datos biométricos.

## Autor

Francisco Quiroga - CENT35 - Tierra del Fuego, Argentina.
