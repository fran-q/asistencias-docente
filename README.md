# Sistema de Asistencias Digital con Reconocimiento Facial

Sistema web para automatizar el registro de asistencia docente en universidades e institutos terciarios mediante reconocimiento facial.

> Proyecto académico - Prácticas Profesionalizantes III - CENT35 - Tierra del Fuego, Argentina.

## Estado

**Primera entrega cerrada (junio 2026)** — los 6 sprints del cronograma
están completos. El sistema cubre: gestión de la institución y administradores,
estructura académica (carreras, materias, comisiones, horarios + grilla
semanal), docentes, consentimiento biométrico (Ley 25.326), reconocimiento
facial con OpenCV/LBPH, pase de asistencia automático, listado con
AUSENTES calculadas, carga manual, justificaciones, y exportación de
reportes a CSV. Documentación completa: ADRs, diagramas UML, manuales
de administrador y técnico, changelog por sprint.

## Stack

- **Java 21** + **Spring Boot 3.5.x**
- **Gradle** (Groovy DSL)
- **MySQL / MariaDB 10.4+** (vía XAMPP en desarrollo local)
- **Spring Data JPA** + **Hibernate** + **Flyway** (migraciones de esquema)
- **Spring Security** (autenticación con sesión HTTP)
- **Thymeleaf** (renderizado server-side)
- **JavaCV / OpenCV** (reconocimiento facial - Sprint 4)

## Características

- Usuarios administradores con aislamiento total entre instituciones (200 a 400 docentes por institución).
- Reconocimiento facial automático con data cifrada (sin almacenar fotografías).
- Carga manual de asistencia como alternativa.
- Cumplimiento de la Ley 25.326 y Resolución AAIP 255/2022 sobre datos biométricos.

## Estructura del proyecto


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
| **S6** | 19-jun a 24-jun | Cierre: reportes CSV + diagramas UML + manuales + changelog | ✅ |

Detalle completo en `docs/1. Guia Proyecto Sistema Asistencias.docx`.

## Documentación

- 📄 [Guía completa del proyecto](docs/1.%20Guia%20Proyecto%20Sistema%20Asistencias.docx)
- 🗂️ [Diagrama de Base de Datos](docs/2.%20Diagrama%20BD%20Sistema%20Asistencias.pdf)
- 📋 [Documento de Requerimientos](docs/3.%20Requerimientos%20Sistema%20Asistencias.docx)
- 🏛️ [Decisiones arquitectónicas (ADR)](docs/adr/)
- 📊 [Diagramas UML](docs/uml/) (PlantUML)
- 📘 [Manual del Administrador](docs/manuales/manual-administrador.md)
- 🔧 [Manual Técnico](docs/manuales/manual-tecnico.md)
- 📜 [CHANGELOG por sprint](CHANGELOG.md)
- 🎬 [Guión sugerido para el video demo](docs/guion-video-demo.md)

## Marco legal

El sistema procesa datos biométricos sensibles. Cumple con:
- **Ley Nacional N° 25.326** de Protección de Datos Personales.
- **Resolución AAIP N° 255/2022** sobre datos biométricos.

## Autor

Francisco Quiroga - CENT35 - Tierra del Fuego, Argentina.
