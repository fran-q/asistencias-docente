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
│   ├── 5-diagramas/                   (diagramas en Mermaid)
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

Detalle completo en [la guía del proyecto](docs/1-catedra/Guia%20del%20proyecto.docx).

## Documentación

Todo el material escrito vive en **[`docs/`](docs/)**, organizado por tipo. Empezá por su
[índice](docs/README.md), que explica qué hay en cada carpeta y por dónde conviene entrar
según lo que necesites.

| | Carpeta | Qué encontrás |
|---|---|---|
| 📄 | [1-catedra](docs/1-catedra) | Guía, requerimientos y diagrama de BD originales |
| 📋 | [2-requerimientos](docs/2-requerimientos) | Alcance, casos de uso, DFD y DER |
| ⚖️ | [3-legal](docs/3-legal) | Consentimiento biométrico y Ley 25.326 |
| 🏛️ | [4-arquitectura](docs/4-arquitectura) | Los 9 ADR, referencia técnica y deuda técnica |
| 📊 | [5-diagramas](docs/5-diagramas) | Diagramas en Mermaid, se ven directo en GitHub |
| 📘 | [6-manuales](docs/6-manuales) | Manual del administrador y manual técnico |
| 📈 | [7-informes](docs/7-informes) | Correcciones y protocolo de calibración |
| 🎬 | [8-defensa](docs/8-defensa) | Apuntes de estudio y guion del video |
| 🖨️ | [9-imprimibles](docs/9-imprimibles) | Versiones PDF listas para imprimir |

Además: [CHANGELOG por sprint](CHANGELOG.md).

## Marco legal

El sistema procesa datos biométricos sensibles. Cumple con:
- **Ley Nacional N° 25.326** de Protección de Datos Personales.
- **Resolución AAIP N° 255/2022** sobre datos biométricos.

## Autor

Francisco Quiroga - CENT35 - Tierra del Fuego, Argentina.
