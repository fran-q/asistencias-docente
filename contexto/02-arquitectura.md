# 02 — Arquitectura

> **Versión:** 1.4 · **Actualizado:** 2026-08-25 · **Estado:** vigente
> **Se actualiza cuando:** se toma una decisión de diseño (y se escribe su ADR).

---

## Stack

| Capa | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java | 21 (toolchain) |
| Framework | Spring Boot | 3.5.14 |
| Build | Gradle (Groovy DSL) | wrapper incluido |
| Base de datos | MariaDB (vía XAMPP en local) | 10.4+ |
| Driver | `org.mariadb.jdbc:mariadb-java-client` | nativo, no el de MySQL |
| Persistencia | Spring Data JPA + Hibernate 6 | — |
| Migraciones | Flyway (`flyway-core` + `flyway-mysql`) | — |
| Seguridad | Spring Security, sesión HTTP con cookie | — |
| Vistas | Thymeleaf server-side + `thymeleaf-extras-springsecurity6` | — |
| Visión por computadora | JavaCV (wrapper de OpenCV) | 1.5.11 / OpenCV 4.10.0 |
| PDF | OpenPDF | 1.3.43 |
| Correo | `spring-boot-starter-mail` | — |
| Boilerplate | Lombok | — |
| Tests | JUnit 5, Mockito, Spring Security Test, H2 en memoria | — |

**Restricción del proyecto:** todo tiene que ser open source y sin costo de licencia
(RNF-18). OpenPDF se eligió sobre PDFBox por eso y porque ya resuelve tablas con ancho
de columna y repetición de encabezado.

## Forma general

Monolito modular server-side. No hay SPA ni API REST pública: Thymeleaf renderiza en el
servidor y el JavaScript del cliente es puntual (cámara, tema, toasts, modales).

Los únicos endpoints que devuelven JSON son los del ciclo de la cámara:
`POST /reconocimiento/detectar` y `POST /asistencia/pase/marcar`.

## Organización del código

**Package-by-layer** (ADR-0006, reemplazó al package-by-feature del ADR-0001):

```
edu.cent35.asistencias
├── controller/   @Controller de Spring MVC y los @ControllerAdvice. Uno por área.
├── service/      Lógica de negocio. @Service + @Transactional.
├── repository/   Spring Data JPA.
├── model/        @Entity, enums, BaseTenantEntity, y el @FilterDef del tenant.
├── dto/          Transporte entre UI y controller, más el estado que vive en sesión.
├── validacion/   Anotaciones de Bean Validation y sus ConstraintValidator.
├── interceptor/  Los HandlerInterceptor: tenant, verificación de cuenta, puesto.
├── seguridad/    Spring Security: el principal, el cargador y la cookie del puesto.
└── config/       Las cuatro @Configuration y el andamiaje del multi-tenant.
```

`validacion/`, `interceptor/` y `seguridad/` son capas, no subpaquetes por feature: agrupan
por **tipo de artefacto** igual que las demás. La regla que sigue vigente es la de no partir
una capa por funcionalidad. Las tres salieron de carpetas donde no pertenecían — las
anotaciones estaban en `dto/` sin ser DTO de nada, y los interceptores y las clases de
Spring Security estaban en `config/` sin ser configuración.

**Por qué `TenantContext` y `TenantFilterAspect` se quedaron en `config/`:** tienen 46 y 6
referencias respectivamente. Moverlos significaba tocar medio proyecto para ganar pureza de
carpeta, en el mecanismo del que depende el aislamiento entre instituciones. Viven donde
`SecurityConfig` y `WebMvcConfig` los cablean.

Recursos:

```
main/resources/
├── db/migration/   V001 consolidado + V016 a V020 — lo que Flyway aplica
├── db/historico/   Las 15 migraciones originales, como referencia. No se aplican.
├── static/css/     main.css (un solo archivo)
├── static/js/      comun/ (transversales), academico/, facial/ (cámara)
├── templates/      Vistas Thymeleaf, agrupadas por área
└── opencv/         Recursos del clasificador facial

test/resources/
└── application-test.properties   Perfil de tests. Acá y no en main/, que lo
                                  metía dentro del JAR de producción.
```

## Multi-tenancy

**Estrategia:** discriminador por columna `institucion_id` (ADR-0002). Una sola base,
una sola instancia, aislamiento por filtro.

Cómo funciona, en orden:

1. `TenantInterceptor` toma la institución del usuario autenticado y la deja en
   `TenantContext`, un `ThreadLocal`.
2. `TenantFilterAspect` activa el filtro de Hibernate `"tenant"` en cada método anotado
   con `@Service`.
3. El `@FilterDef` global está declarado en `model/package-info.java`, con la condición
   `institucion_id = :institucionId`.
4. Cada entidad tenant-scoped extiende `BaseTenantEntity` y suma su propio
   `@Filter(name = "tenant")`.

### La trampa que hay que conocer

El filtro de Hibernate **se aplica a la entidad raíz del SELECT, no a las entidades
JOINeadas desde JPQL**. Una query como `SELECT c FROM Comision c JOIN c.materia m`
devuelve comisiones de *todas* las instituciones, porque `Comision` no es tenant-scoped
y el JOIN a `Materia` no arrastra el filtro.

**Regla obligatoria:** toda `@Query` JPQL o nativa con JOIN a una entidad tenant-scoped
lleva `WHERE x.institucionId = :tenantId` explícito. Detalle en `04-convenciones.md` y
en TD-003.

### Defensa en profundidad

Son cuatro capas, y existen las cuatro porque cada una falló alguna vez:

1. Filtro de Hibernate sobre la entidad raíz.
2. `WHERE` explícito en cualquier JOIN.
3. Validación en el service: se verifica que el resultado pertenezca al tenant. Si no,
   se responde "no encontrado" — nunca "no autorizado", que revelaría que el id existe
   en otra institución.
4. IDs secuenciales no adivinables por contexto + camuflado del cross-tenant.

**Lección de TD-007:** el pointcut del aspecto apuntaba por nombre de paquete. La
reorganización a package-by-layer lo dejó apuntando a un paquete inexistente, y el
aspecto quedó inactivo **en silencio** durante semanas. Hoy el pointcut va por anotación
(`@within(org.springframework.stereotype.Service)`), que sobrevive a un renombre de
paquetes. Un aspecto que no matchea no falla: no hace nada.

## Autenticación

Sesión HTTP con cookie clásica, no JWT (ADR-0003). Contraseñas con BCrypt.
`spring.jpa.open-in-view=false`. Timeout de sesión: 30 minutos, configurable.

Verificación de correo y recuperación de contraseña por código de un solo uso enviado
por mail (ADR-0009), con bloqueo de la cuenta hasta verificar (ADR-0010). Antes de eso,
si el superadmin de una institución olvidaba la contraseña, la institución quedaba sin
acceso.

## Reconocimiento facial

**Algoritmo:** LBPH de OpenCV contrib, vía JavaCV (ADR-0007). Un modelo entrenado **por
docente**, con varias capturas de esa misma persona.

⚠ **Desvío conocido:** el documento de requerimientos (RF-08) habla de *embeddings*.
LBPH no genera un embedding vectorial: genera un modelo entrenado que OpenCV serializa
como YAML. La columna se llama `embedding_cifrado` por herencia del diseño original.
Está documentado como desvío en `05-trazabilidad.md`.

**Pipeline de registro** (`POST /docentes/{id}/rostro/registrar`):

1. Se verifica que el docente tenga **consentimiento biométrico vigente**. Sin eso, no
   se avanza.
2. Captura guiada por poses (ADR-0012). El navegador filtra por calidad antes de enviar,
   y el servidor revalida — el cliente decide *cuándo* capturar, no *si la captura sirve*.
3. Se exige un mínimo de capturas válidas y que los recortes sean distintos entre sí.
4. Entrenamiento LBPH local.
5. **gzip antes de cifrar.** El YAML de OpenCV es muy repetitivo y comprime entre 5x y
   10x. Sin esto el INSERT supera `max_allowed_packet` de MariaDB y corrompe tablas del
   sistema. No es una optimización: es un requisito.
6. Cifrado AES-256-GCM con Spring Security Crypto.
7. Persistencia en `modelos_faciales.embedding_cifrado` (`LONGBLOB`, con
   `@JdbcTypeCode(SqlTypes.LONGVARBINARY)` para que Hibernate 6 no espere un `BLOB` chico).

**Pipeline de identificación** (`POST /asistencia/pase/marcar`):

1. El navegador manda fotogramas por `getUserMedia`.
2. `DeteccionRostroService` localiza el rostro.
3. `MotorLbphService` compara contra los modelos cacheados en memoria.
4. Se exige superar el umbral **y** un margen mínimo contra el segundo candidato
   (ADR-0014) — que una cara se parezca a alguien no alcanza si se parece casi igual a
   otro.
5. `VentanaConfirmacionService` (ADR-0013) evita marcar por un frame suelto.
6. `BloquePresenciaService` decide **qué significa** esa pasada por la cámara: sin bloque
   abierto es una entrada, con bloque abierto y la permanencia mínima cumplida es una
   salida. No hay selector en la pantalla — lo decide el estado del docente (ADR-0017).
7. Se clasifica según `horarios.tolerancia_min`, que es **por horario** y perdona hacia los
   dos lados: PRESENTE hasta `hora_inicio + tolerancia`, y salida en hora desde
   `hora_fin - tolerancia` (ADR-0018).
8. Se persiste: al entrar, el bloque más la clase en curso; al salir, el cierre del bloque
   más las clases que quedaron cubiertas.

**Los pasos 1 a 5 son idénticos para una entrada y para una salida.** La misma cara, el
mismo modelo, el mismo umbral: lo único que cambia es qué se hace con la identidad una vez
confirmada.

**Idempotencia en tres niveles:** UNIQUE en base + verificación en el service + pausa de
5 segundos en el frontend tras cada marca exitosa. Contra la condición de carrera:
`saveAndFlush` y captura de `DataIntegrityViolationException`. Desde ADR-0017 la base
también garantiza que un docente **no tenga dos bloques abiertos a la vez**, con un UNIQUE
sobre una columna generada que vale su id solo mientras el bloque siga abierto.

### Bloque de presencia

La unidad de la que se predican una entrada y una salida **no es el horario**: es el lapso
continuo en que la persona estuvo en la institución. Un bloque agrupa todas las clases
consecutivas separadas por menos que el umbral de la institución
(`instituciones.umbral_separacion_min`), sin importar de qué materia sean, y una sola
entrada y una sola salida las cubren a todas.

El horario sigue siendo la unidad de la **asistencia**; el bloque es la unidad de la
**permanencia**. Por eso viven en dos tablas y `asistencias` no cambió de significado.

Tres reglas que no son obvias:

- **Al entrar se marca solo la clase en curso.** Las siguientes se imputan al cerrar, según
  lo que el docente haya cubierto: marcarlas antes asentaría como dictada una clase que
  todavía no empezó.
- **La asistencia no depende del dato de salida.** Si nadie la registra, el job imputa
  igual usando el fin de la última clase, marca la hora como `PRESUNTO` y deja el bloque
  como pendiente. Lo que queda sin resolver es la hora, no la asistencia.
- **Sin consentimiento vigente no se abre ni se cierra un bloque.** La entrada ya
  registrada se conserva: era lícita cuando ocurrió.

**Cache:** `ConcurrentHashMap<Long, LBPHFaceRecognizer>` en memoria, con **barrido por
inactividad**: `descartarModelosInactivos` es un `@Scheduled` que suelta los modelos que
nadie usó en `app.biometria.cache-minutos-inactividad` (30 por defecto). No alcanzaba con
limpiar dentro de `sincronizarCache`, que corre cuando alguien identifica: el momento en
que hay que soltar la memoria es justamente cuando ya nadie identifica.

No es solo una cuestión de memoria. Un modelo cacheado está **descifrado**, así que cada
minuto de más en el cache es un minuto más en que ese dato biométrico existe en claro.

`sincronizarCache` sigue corriendo en cada identificación, así que un docente dado de baja
—o uno que revocó su consentimiento— se evicta en segundos.

## Ausencias

No se marcan: se generan. `GeneradorAusenciasService` corre por cron
(`app.asistencia.ausencias-cron`, por defecto cada 30 minutos) y materializa las
ausencias de los horarios cuyo `hora_fin` ya pasó sin marca. El listado además calcula
al vuelo la ventana entre el fin de la clase y la corrida siguiente.

Desde ADR-0017 el job hace **dos cosas, y en este orden**: primero cierra los bloques que
quedaron sin marca de salida —imputando las clases que el docente cubrió— y recién después
genera las ausencias. El orden no es indistinto: al revés, la ausencia se escribiría primero
y la imputación chocaría contra el UNIQUE, dejando como ausente una clase que sí se dio.

## Entornos

| Perfil | Para qué | Configuración |
|---|---|---|
| (base) | Común a todos | `application.properties` — versionado, sin secretos |
| `local` | Desarrollo | `application-local.properties` — **gitignored**, credenciales de XAMPP. Lo activa `./gradlew bootRun` |
| `test` | Tests y CI | `src/test/resources/application-test.properties` — H2 en memoria |

`spring.jpa.hibernate.ddl-auto=validate`: el esquema lo maneja Flyway, Hibernate solo
verifica que las entidades coincidan. Zona horaria fijada en `America/Argentina/Ushuaia`.

**CI:** GitHub Actions sobre `push` y `pull_request` a `main`. JDK 21 Temurin,
`./gradlew build --no-daemon` sobre H2. Sube el reporte si falla. Ese `build` corre las
dos suites —unitarios e integración— porque `check` depende de ambas; ver `04-convenciones.md`.

## Los 18 ADR

Viven en `Documentacion/4-arquitectura/adr/`. Un ADR dice **por qué** se decidió algo y
qué se descartó.

| # | Título | Estado |
|---|---|---|
| 0001 | Monolito modular | ⚠ Reemplazada por 0006 |
| 0002 | Multi-tenancy por discriminador | Vigente |
| 0003 | Sesión con cookie vs JWT | Vigente |
| 0004 | Tenant filter en JOINs | Vigente |
| 0005 | Consentimiento biométrico | Vigente |
| 0006 | Organización por capas | Vigente |
| 0007 | Reconocimiento facial con LBPH | Vigente |
| 0008 | Asistencia automática | Vigente |
| 0009 | Verificación de correo y recuperación | Vigente |
| 0010 | Alta de institución y bloqueo por verificación | Vigente |
| 0011 | Errores de integridad legibles | Vigente |
| 0012 | Captura guiada del rostro | Vigente |
| 0013 | Ventana de confirmación del pase | Vigente |
| 0014 | Margen mínimo contra el segundo candidato | Vigente |
| 0015 | Puesto autorizado para la captura biométrica | Propuesta |
| 0016 | Persona separada de usuario y de vínculo docente | Propuesta |
| 0017 | Marca de salida y bloque de presencia | Vigente |
| 0018 | La tolerancia es simétrica y bidireccional | Vigente — reemplaza la decisión 1 de ADR-0008 |
