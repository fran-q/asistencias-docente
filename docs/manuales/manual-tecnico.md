# Manual Técnico

**Sistema de Asistencias Digital con Reconocimiento Facial**
*Prácticas Profesionalizantes III — CENT35*

---

## Índice

1. [Stack y arquitectura](#1-stack-y-arquitectura)
2. [Prerrequisitos](#2-prerrequisitos)
3. [Instalación paso a paso](#3-instalación-paso-a-paso)
4. [Estructura del repositorio](#4-estructura-del-repositorio)
5. [Configuración y propiedades](#5-configuración-y-propiedades)
6. [Base de datos y migraciones](#6-base-de-datos-y-migraciones)
7. [Reconocimiento facial (JavaCV/OpenCV)](#7-reconocimiento-facial-javacvopencv)
8. [Multi-tenancy](#8-multi-tenancy)
9. [Seguridad y cifrado biométrico](#9-seguridad-y-cifrado-biométrico)
10. [Tests](#10-tests)
11. [Backup y restauración](#11-backup-y-restauración)
12. [Troubleshooting](#12-troubleshooting)
13. [Despliegue en producción](#13-despliegue-en-producción)

---

## 1. Stack y arquitectura

- **Java 21** + **Spring Boot 3.5.x**
- **Gradle** (Groovy DSL) — build, dependencias, ejecución.
- **MariaDB 10.4+** (en desarrollo: XAMPP en Windows).
- **Spring Data JPA** + **Hibernate 6** — ORM y validación de esquema.
- **Flyway** — migraciones versionadas (V001 a V006).
- **Spring Security** — autenticación con sesión HTTP (cookie clásica, ver ADR-0003).
- **Thymeleaf** — server-side rendering.
- **JavaCV 1.5.11 + OpenCV 4.10** — reconocimiento facial.

**Organización**: package-by-layer (`controller/`, `service/`, `repository/`,
`model/`, `dto/`, `config/`). Ver ADR-0006.

---

## 2. Prerrequisitos

- **JDK 21**: `java -version` debe devolver 21.x. Recomendado: Eclipse Temurin
  o el JDK de Oracle.
- **XAMPP 8.x+** con MariaDB 10.4 (Windows). En Linux/macOS usar MariaDB
  oficial.
- **IntelliJ IDEA Community** (recomendado) o cualquier IDE con soporte
  Lombok.
- **Git**.
- **Webcam funcional** (para probar el reconocimiento facial en local).

---

## 3. Instalación paso a paso

### 3.1 Clonar el repositorio
```bash
git clone https://github.com/fran-q/asistencias-docente.git
cd asistencias-docente/asistencias
```

### 3.2 Crear la base de datos y el usuario en MariaDB

Iniciar XAMPP → arrancar MySQL/MariaDB → abrir phpMyAdmin (`http://localhost/phpmyadmin`).
En la pestaña SQL como `root`:

```sql
CREATE DATABASE asistenciautomatica
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'asistencias'@'localhost'
  IDENTIFIED BY 'CAMBIAR_POR_TU_PASSWORD_LOCAL';

GRANT ALL PRIVILEGES ON asistenciautomatica.*
  TO 'asistencias'@'localhost';

FLUSH PRIVILEGES;
```

### 3.3 Crear el archivo de configuración local

`src/main/resources/application-local.properties` (este archivo **no** se
versiona en git):

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/asistenciautomatica?useUnicode=true&characterEncoding=UTF-8
spring.datasource.username=asistencias
spring.datasource.password=TU_PASSWORD
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

logging.level.edu.cent35=DEBUG
spring.jpa.show-sql=true
spring.thymeleaf.cache=false
spring.devtools.restart.enabled=true
```

### 3.4 Subir el `max_allowed_packet` de MariaDB (importante)

Editar `C:\xampp\mysql\bin\my.ini`. En la sección `[mysqld]`:

```ini
max_allowed_packet=64M
```

Y en `[mysql]` y `[client]` si aparecen, también ponerlo en `64M`.

> El modelo LBPH entrenado + cifrado puede pesar varios MB. Sin este ajuste,
> el INSERT puede romper la conexión y corromper tablas del sistema.

Reiniciar MySQL en XAMPP.

### 3.5 Levantar la app
```bash
./gradlew bootRun
```

Activa el perfil `local` automáticamente. Flyway aplica V001..V006 al
arrancar por primera vez.

**Login inicial**: se crea con una migración seed (`V002__seed_test_data.sql`).
Usuario y contraseña están en `Documentacion/credenciales_proyecto.txt` (no
versionado).

Acceder en `http://localhost:8080`.

---

## 4. Estructura del repositorio

```
asistencias/
├── docs/                              ← documentación versionada
│   ├── adr/                           (Architectural Decision Records)
│   ├── manuales/                      (este manual + manual administrador)
│   ├── uml/                           (diagramas PlantUML)
│   └── legal/                         (textos legales)
├── gradle/                            (wrapper de Gradle)
├── src/
│   ├── main/
│   │   ├── java/edu/cent35/asistencias/
│   │   │   ├── AsistenciasApplication.java
│   │   │   ├── controller/            (Spring @Controller)
│   │   │   ├── service/               (lógica de negocio @Service)
│   │   │   ├── repository/            (Spring Data JPA)
│   │   │   ├── model/                 (@Entity, enums, BaseTenantEntity)
│   │   │   ├── dto/                   (transporte UI ↔ controller)
│   │   │   └── config/                (security, multi-tenancy, web/JPA)
│   │   └── resources/
│   │       ├── db/migration/          (V001..V006 Flyway)
│   │       ├── opencv/                (haarcascade XML)
│   │       ├── static/                (CSS / JS)
│   │       ├── templates/             (Thymeleaf)
│   │       └── application*.properties
│   └── test/
├── build.gradle
├── gradle.properties
└── README.md
```

---

## 5. Configuración y propiedades

### 5.1 `application.properties` (versionado, sin secretos)

Propiedades clave:

| Propiedad | Default | Para qué |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | Flyway maneja el esquema; Hibernate solo valida. |
| `spring.jpa.open-in-view` | `false` | Evita N+1 ocultos. Las queries deben usar `JOIN FETCH` o el service hacer `touch` lazy. |
| `server.tomcat.max-http-form-post-size` | `20MB` | Permite POSTs grandes con frames base64. |
| `app.biometria.clave-cifrado` | `${BIOMETRIA_CLAVE:dev-...}` | Clave AES para los modelos. Override por env var en producción. |
| `app.biometria.salt` | hex | Salt PBKDF2. |
| `app.biometria.umbral-confianza` | `100.0` | Distancia máxima LBPH para considerar match. |
| `app.biometria.tamano-rostro` | `200` | Lado en px del rostro normalizado. |
| `app.biometria.duracion-grabacion-seg` | `30` | Duración de la grabación de registro facial. |

### 5.2 `application-local.properties` (NO versionado)

Credenciales de la BD local del desarrollador. Ver sección 3.3.

### 5.3 `application-test.properties` (versionado, sin secretos)

H2 en memoria, Flyway desactivado, Hibernate genera el esquema con
`create-drop`. Lo usa el bloque `./gradlew test`.

### 5.4 `gradle.properties`

```properties
javacpp.platform=windows-x86_64
```

Restringe la descarga de binarios nativos de OpenCV solo a Windows
(~150 MB en lugar de ~1 GB). Si el CI corre en Linux, sobrescribir
con `-Pjavacpp.platform=linux-x86_64`.

---

## 6. Base de datos y migraciones

Las migraciones viven en `src/main/resources/db/migration/`:

| Versión | Qué hace |
|---|---|
| `V001__init.sql` | Esquema completo inicial (todas las tablas con CHECK constraints). |
| `V002__seed_test_data.sql` | Seed de instituciones, usuarios y datos de prueba. |
| `V003__rename_rol_superadmin_to_institucion.sql` | Renombra el rol SUPERADMIN_INSTITUCION a INSTITUCION. |
| `V004__comisiones_docente_nullable.sql` | Hace `docente_asignado_id` nullable mientras se construía el módulo de docentes. |
| `V005__consentimientos_biometricos_audit.sql` | Agrega columnas de auditoría forense (IP, UA, motivo de revocación, timestamps). |
| `V006__modelos_faciales_mediumblob.sql` | Cambia `embedding_cifrado` de BLOB a LONGBLOB. |

Cualquier cambio futuro de esquema = nueva V007__... No se editan migraciones
ya aplicadas.

---

## 7. Reconocimiento facial (JavaCV/OpenCV)

### 7.1 Componentes en el código

- `service/DeteccionRostroService` — Haar Cascade para detección. Carga el
  XML desde `resources/opencv/haarcascade_frontalface_default.xml` al arrancar.
- `service/MotorLbphService` — entrena `LBPHFaceRecognizer`, serializa el
  modelo a YAML, lo comprime con **gzip** (5-10× menos bytes).
- `service/CifradoBiometricoService` — AES-256-GCM via Spring Security Crypto.
- `service/ModeloFacialService` — orquesta el registro (Sprint 4 Fase C).
- `service/IdentificacionFacialService` — orquesta la identificación con
  cache en memoria de recognizers (Sprint 4 Fase D).
- `service/PaseAsistenciaService` — fachada para Sprint 5 (identificación
  + marcado de asistencia).

### 7.2 Calibración del umbral

Si el sistema rechaza rostros reales, **subí** `app.biometria.umbral-confianza`
de 100 a 130 o 150. Si confunde caras (falsos positivos), bajalo a 70-80.

Valores típicos LBPH (distancia menor = match):
- `< 50` — match muy seguro.
- `50-100` — match razonable.
- `100-130` — match dudoso.
- `> 130` — probablemente no es la misma persona.

### 7.3 Memoria nativa

Las Mat y Recognizers de OpenCV reservan memoria fuera del heap de la JVM.
El código usa `try-with-resources` o `finally` para cerrarlos. Si ves uso de
RAM creciente, revisar que cualquier `Mat` nueva tenga su `.close()`.

---

## 8. Multi-tenancy

Tres niveles de defensa (ADR-0002 y ADR-0004):

1. **Filtro Hibernate** `@Filter("tenant")` aplicado automáticamente a entidades
   con columna `institucion_id` (las que extienden `BaseTenantEntity`).
2. **`@Query` con `WHERE m.institucionId = :tenantId`** explícito en queries
   con JOIN — el filtro de Hibernate **no se propaga** por JPQL JOIN.
3. **Validación explícita en services**: `obtenerXValidado(id, tenantId)`
   antes de cualquier operación.

El `TenantContext` (ThreadLocal) se popula en cada request desde el
`CustomUserDetails` autenticado, vía `TenantInterceptor`.

---

## 9. Seguridad y cifrado biométrico

- **BCrypt** para contraseñas de usuarios (RNF-06).
- **AES-256-GCM** vía Spring Security Crypto para los modelos faciales
  (ADR-0007). La clave deriva con PBKDF2 de `app.biometria.clave-cifrado`
  y `app.biometria.salt`.
- **CSRF** habilitado por defecto. El meta tag `_csrf` en `layout/base.html`
  lo usan los fetch POST del JS.
- **Sesión HTTP cookie** (`JSESSIONID`). Logout limpia la cookie.
- **No se almacenan imágenes** (cumple Ley 25.326).

### Rotación de la clave de cifrado

Cambiar `app.biometria.clave-cifrado` **invalida todos los modelos cifrados
con la clave anterior**. Para rotarla sin perder modelos, hay que escribir
una utilidad que descifre con la vieja y vuelva a cifrar con la nueva
(no implementado en Sprint 6).

---

## 10. Tests

```bash
./gradlew test
```

Reportes en `build/reports/tests/test/index.html`.

Cobertura:
- Services con Mockito (lógica de negocio).
- `OpenCvSmokeTest` valida que los binarios nativos de OpenCV cargan.
- Tests integradores con MockMvc (Sprint 6 Fase D).

Los tests usan el perfil `test` (H2 en memoria).

---

## 11. Backup y restauración

### Backup completo de la BD

```bash
# En Windows con XAMPP:
C:\xampp\mysql\bin\mysqldump.exe -u root asistenciautomatica > backup_YYYYMMDD.sql

# En Linux:
mysqldump -u asistencias -p asistenciautomatica > backup_YYYYMMDD.sql
```

### Restaurar

```bash
# Crear la BD si no existe
mysql -u root -e "CREATE DATABASE IF NOT EXISTS asistenciautomatica CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Restaurar
mysql -u root asistenciautomatica < backup_YYYYMMDD.sql
```

### Backup de modelos faciales solo

Si querés exportar solo los `modelos_faciales` (por ejemplo para migrar a otro
servidor):
```bash
mysqldump -u root asistenciautomatica modelos_faciales > modelos.sql
```
Para que sean útiles en el destino, la clave `app.biometria.clave-cifrado`
**debe ser la misma** (sino, no se pueden descifrar).

---

## 12. Troubleshooting

### MySQL no arranca en XAMPP
- Ver `C:\xampp\mysql\data\mysql_error.log`.
- Si dice **"Can't open and lock privilege tables: Incorrect file format
  'global_priv'"**: una tabla del sistema MariaDB se corrompió. Restaurar
  desde `C:\xampp\mysql\backup\mysql\*` (ver historial git para los pasos).

### La app no arranca con `Schema-validation: wrong column type`
- Hibernate espera un tipo distinto al que tiene la columna. Revisar la
  entidad vs la migración. Si la columna es `LONGBLOB`, el mapeo debe ser
  `@JdbcTypeCode(SqlTypes.LONGVARBINARY)`.

### `Connection reset by peer` al guardar modelo facial
- El paquete superó `max_allowed_packet`. Ver sección 3.4.

### La cámara no enciende en el navegador
- Permitir el acceso a la cámara para `localhost`.
- Cerrar otras apps que usen la cámara (Zoom, Meet, etc.).
- `getUserMedia` solo funciona en HTTPS o en `localhost`. Si accedés por IP
  de red, hay que ponerle HTTPS.

### Reconocimiento muy permisivo o muy estricto
- Ajustar `app.biometria.umbral-confianza`. Ver sección 7.2.

---

## 13. Despliegue en producción

> **El sistema fue desarrollado como PoC académico**. Para producción real
> hay varias cosas que pulir.

### Imprescindible para producir
1. **Variables de entorno**: mover `app.biometria.clave-cifrado`,
   `app.biometria.salt`, credenciales de BD a variables de entorno o a un
   gestor de secretos (Vault, AWS Secrets Manager).
2. **HTTPS obligatorio**. La cámara solo funciona en HTTPS fuera de `localhost`.
3. **Backups automáticos** de la BD (diarios mínimo).
4. **Logs estructurados** (JSON) e ingest a un sistema de observabilidad.
5. **Monitoreo**: Actuator + Prometheus + alertas.

### Recomendado
- Migrar a un motor de reconocimiento con embeddings (FaceNet/ArcFace) si la
  cantidad de docentes pasa los miles. LBPH es O(N) por verificación.
- Cron de backup de `modelos_faciales` cifrados off-site.
- Tests de carga (apache-bench, k6) si se prevé pico de uso simultáneo.

---

*Última actualización: Sprint 6.*
