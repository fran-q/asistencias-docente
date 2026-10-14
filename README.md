# Visum

[![CI](https://github.com/fran-q/asistencias-docente/actions/workflows/ci.yml/badge.svg)](https://github.com/fran-q/asistencias-docente/actions/workflows/ci.yml)

Registro de asistencia **docente** por reconocimiento facial, para institutos terciarios y
universidades de Tierra del Fuego. Reemplaza la planilla de papel con firma.

> Proyecto académico — Prácticas Profesionalizantes III, CENT 35. Se trabaja y se documenta
> como un producto para un cliente real: el equipo administrativo de la institución.

**Sobre el nombre.** El producto se llama Visum; el repositorio, el paquete Java
(`edu.cent35.asistencias`) y la aplicación todavía dicen "asistencias". Es una inconsistencia
conocida y pendiente de unificar.

## Estado

Prototipo funcional completo, con instalador para la máquina de la institución que funciona
sin internet ni servidor de correo. El horizonte de producto final es marzo de 2027; qué falta
y en qué orden está en [`contexto/07-pendientes.md`](contexto/07-pendientes.md).

## Por dónde empezar

Por **[`contexto/`](contexto/)**. Es la documentación de trabajo y responde, en este orden:

| Archivo | Qué responde |
|---|---|
| [`01-proyecto.md`](contexto/01-proyecto.md) | Qué es, para quién, qué queda dentro y fuera del alcance |
| [`02-arquitectura.md`](contexto/02-arquitectura.md) | Stack, capas, multi-tenancy, reconocimiento facial y el índice de los ADR |
| [`03-modelo-datos.md`](contexto/03-modelo-datos.md) | Las tablas vigentes, sus invariantes y el historial de migraciones |
| [`04-convenciones.md`](contexto/04-convenciones.md) | Cómo se escribe código acá. **Leer antes de tocar nada** |
| [`05-trazabilidad.md`](contexto/05-trazabilidad.md) | Cada requerimiento, su estado real y dónde vive en el código |
| [`06-glosario.md`](contexto/06-glosario.md) | El vocabulario del dominio |
| [`07-pendientes.md`](contexto/07-pendientes.md) | Deuda técnica, brechas y rumbo hacia el producto final |

## Qué hace

- **Varias instituciones en una misma instalación**, aisladas entre sí.
- **Reconocimiento facial** con LBPH (JavaCV/OpenCV). Nunca se guarda una fotografía: solo el
  modelo derivado, comprimido y cifrado.
- **Consentimiento biométrico** según la Ley 25.326 y la Resolución AAIP 255/2022, con registro
  de quién lo otorgó y quién lo revocó.
- **Entrada y salida** de cada jornada, desde la cámara de secretaría o desde un kiosco por
  puerta, con varios equipos por institución.
- **Ausencias generadas solas** al terminar cada clase, carga manual con motivo y
  justificaciones.
- **Reportes** en pantalla, PDF, CSV y gráficos.

## Stack

- **Java 21** y **Spring Boot 3.5**, con **Gradle**
- **MariaDB 10.4** — *solo 10.4*: el esquema no aplica sobre 10.6 en adelante (TD-011, en
  [`07-pendientes.md`](contexto/07-pendientes.md))
- **Spring Data JPA**, **Hibernate** y **Flyway** para las migraciones
- **Spring Security**, con sesión HTTP
- **Thymeleaf** para las vistas; el CSS y el JavaScript son propios, sin librerías de terceros
- **JavaCV / OpenCV** para el reconocimiento facial y **OpenPDF** para los reportes

## Correr en local

1. **MariaDB 10.4** (XAMPP sirve). Crear las dos bases: la primera guarda los datos y la segunda,
   el historial de Flyway.

   ```sql
   CREATE DATABASE asistenciautomatica      CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   CREATE DATABASE asistenciautomatica_meta CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```

2. **La configuración local.** Copiar
   `src/main/resources/application-local.properties.ejemplo` a
   `src/main/resources/application-local.properties` y completar el usuario y la contraseña de
   la base. El archivo real está en `.gitignore` y nunca se sube.

3. **Arrancar.** `bootRun` activa solo el perfil `local`, y Flyway arma las tablas al arrancar.

   ```bash
   ./gradlew bootRun
   ```

4. **La primera cuenta.** Entrar a `http://localhost:8080/alta-institucion` y dar de alta una
   institución. Con `app.mail.canal=consola`, el código de verificación sale impreso en la
   terminal en lugar de mandarse por correo.

## Tests

| Comando | Qué corre |
|---|---|
| `./gradlew test` | Los unitarios. Corren en segundos y no necesitan MariaDB |
| `./gradlew integrationTest` | Los de integración (`src/test/java/.../integracion/`), que levantan la aplicación entera |
| `./gradlew build` | Las dos cosas, más Checkstyle (`config/checkstyle/`). Es lo que corre el CI en cada push y en cada pull request |

`MigracionesIT` aplica las migraciones contra un MariaDB real y se saltea si no hay uno en
`localhost:3306`. En el CI corre siempre: el workflow levanta un `mariadb:10.4`.

## Estructura

```
asistencias/
├── contexto/              La documentación de trabajo. Empezar acá.
├── instalador/            Instalación en la máquina de la institución: .msi, scripts y guía
├── src/main/java/edu/cent35/asistencias/
│   ├── controller/        Pantallas y endpoints
│   ├── service/           Lógica de negocio
│   ├── repository/        Acceso a datos (Spring Data JPA)
│   ├── model/             Entidades JPA y enums
│   ├── dto/               Formularios, filas de pantalla y estado de la sesión
│   ├── validacion/        Validaciones propias de Bean Validation
│   ├── seguridad/         Autenticación, sesiones y la credencial del equipo de captura
│   ├── interceptor/       Controles por petición: institución, equipo autorizado, verificación
│   └── config/            Configuración de Spring y el contexto de institución
├── src/main/resources/
│   ├── db/migration/      Migraciones de Flyway. Las ya aplicadas no se editan nunca.
│   ├── templates/         Vistas Thymeleaf, una carpeta por área
│   └── static/            CSS y JavaScript
├── src/test/java/         Unitarios por capa, y de integración en integracion/
└── CHANGELOG.md           Qué cambió y por qué, en orden cronológico inverso
```

El código está organizado **por capas**. La organización original era por *feature*; se migró
a pedido de la cátedra, y el porqué, lo que se gana, lo que se pierde y cómo volver atrás están
en el ADR-0006.

## Documentación extensa

Los ADR, los requerimientos, el marco legal y el material de la defensa viven en
`Documentacion/`, **fuera de este repositorio**, un nivel arriba del proyecto. Se sacaron a
propósito: el push dejaba de arrastrar documentos de Word y PDF que cambian enteros en cada
guardada aunque se toque una coma. El historial anterior a agosto de 2026 los conserva.
Desde octubre de 2026 se versionan aparte, en un repositorio privado.

```
Documentacion/
├── 1-catedra/          La consigna original de la cátedra
├── 2-requerimientos/   Requerimientos, alcance, casos de uso y DER
├── 3-legal/            Consentimiento biométrico y Ley 25.326
├── 4-arquitectura/     Los ADR, la referencia técnica y la deuda técnica
├── 5-informes/         Calibración del umbral, revisión de UI/UX y bitácoras
└── 6-defensa/          Dossier, preguntas del tribunal y guiones
```

Su índice es `Documentacion/README.md`.

## Marco legal

El sistema trata datos biométricos, que la ley considera sensibles. Cumple con:

- la **Ley Nacional 25.326** de Protección de Datos Personales;
- la **Resolución AAIP 255/2022** sobre datos biométricos.

## Autor

Francisco Quiroga — CENT 35, Tierra del Fuego, Argentina.
