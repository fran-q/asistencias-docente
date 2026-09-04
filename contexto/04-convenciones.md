# 04 — Convenciones

> **Versión:** 1.5 · **Actualizado:** 2026-08-25 · **Estado:** vigente
> **Se actualiza cuando:** se adopta o se abandona una convención.

Este es el archivo que hay que leer antes de escribir una línea de código.

---

## Idioma

**El código está en español.** Clases, métodos, variables, comentarios, mensajes de
error, columnas de base de datos. `PaseAsistenciaService`, `ManejadorDeColisiones`,
`buscarPorId`, `suprimirDatosBiometricos`.

Las únicas excepciones son lo que impone el framework (`findById`, `@GetMapping`,
`save`) y los términos técnicos sin traducción razonable (`embedding`, `cache`,
`pointcut`).

Los identificadores llevan tildes y eñes donde corresponde en comentarios y mensajes,
nunca en nombres de clase o método. Por eso el `build.gradle` fija
`options.encoding = 'UTF-8'`: sin eso la compilación depende del locale de la máquina.

## Estructura

Package-by-layer (ADR-0006). Un archivo nuevo va a `controller/`, `service/`,
`repository/`, `model/`, `dto/`, `validacion/`, `interceptor/`, `seguridad/` o `config/`
según **qué hace**, no según a qué funcionalidad pertenece.

No crear subpaquetes por feature dentro de las capas. Se probó y se abandonó.

Sumar una capa nueva es otra cosa y es legítimo, siempre que agrupe por **tipo de
artefacto** y no por funcionalidad: así nació `validacion/`, con las anotaciones de Bean
Validation y sus `ConstraintValidator`, que hasta entonces vivían en `dto/` sin ser DTO de
nada. La prueba a pasar es simple: si el nombre de la carpeta es un sustantivo del dominio
(`docentes/`, `asistencia/`), está mal.

Dos aclaraciones sobre capas que ya existían y se prestaban a duda:

- Los `@ControllerAdvice` van en `controller/`. Manejan peticiones y devuelven vistas;
  `config/` es configuración. Estuvieron repartidos entre las dos capas un tiempo.
- En `config/` entran las `@Configuration` y nada más, con una excepción a propósito:
  `TenantContext` y `TenantFilterAspect`. Los `HandlerInterceptor` van en `interceptor/`
  y lo de Spring Security en `seguridad/`.
- En `dto/` entra también el estado que vive en la sesión HTTP (`AltaPendiente`,
  `ConfirmacionIdentidad`): son objetos de transporte, no lógica de negocio, y estaban en
  `service/` sin llevar `@Service`.

## Comentarios

Convención unificada en los 181 archivos fuente. Se respeta.

**Por archivo:** un Javadoc de dos oraciones arriba de la clase. Primera oración: qué
hace. Segunda: la particularidad que hay que saber antes de tocarlo.

```java
/**
 * ABM de las carreras de la institución actual (RF-11), con baja lógica en vez de borrado.
 * El aislamiento se apoya en el filtro de Hibernate y además valida el tenant a mano en
 * buscarPorId, porque findById no pasa por el filtro.
 */
```

**Por método:** una línea `//` arriba, en presente, que diga qué hace.

```java
// Busca por id validando que pertenezca al tenant actual.
```

**Comentarios largos:** solo para explicar **por qué**, nunca **qué**. El qué se lee en
el código. Los buenos comentarios de este proyecto explican una decisión no obvia o una
trampa que ya mordió a alguien:

```java
// El navegador ya filtró con estos mismos criterios antes de mandar, pero se
// revalida igual: el cliente decide CUÁNDO capturar, no si la captura sirve.
```

**Referenciar el requerimiento o el ADR** cuando aplique: `(RF-11)`, `(RNF-14)`,
`Ver ADR-0007`. Es lo que hace navegable el código desde la documentación.

⚠ Los comentarios que mienten son peor que no tener comentarios. Durante la revisión
post-sprint aparecieron comentarios que afirmaban que el filtro de tenant "se activará
en la Fase B" cuando ya estaba activo. Si se cambia el comportamiento, se cambia el
comentario en el mismo commit.

## Regla de oro del multi-tenant

**Toda `@Query` JPQL o nativa con `JOIN` a una entidad tenant-scoped lleva el `WHERE`
explícito.**

```java
@Query("""
    SELECT c FROM Comision c
    JOIN c.materia m
    WHERE m.institucionId = :tenantId
    ORDER BY ...
    """)
List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);
```

El service pasa `TenantContext.getRequired()`.

| Caso | ¿Aplica la regla? |
|---|---|
| `@Query` JPQL/HQL con JOIN a entidad filtrada | **Sí** |
| Query nativa (`nativeQuery = true`) | **Sí** |
| Derived query de Spring Data (`findByMateriaIdAndCodigo`) | No — genera JPQL sobre la raíz, sin JOIN explícito |
| `findAll()` sobre entidad que sí tiene `@Filter` | No — el filtro funciona normal |

**Además:** `findById` no pasa por el filtro. Cualquier service que lo use tiene que
validar el tenant a mano y, si no coincide, responder "no encontrado" —nunca "no
autorizado"—, para no revelar que ese id existe en otra institución. Y loguear el
intento con `log.warn`.

## Servicios

- `@Service` + `@RequiredArgsConstructor` (Lombok) + `@Slf4j`.
- Dependencias por constructor, campos `private final`. Nada de `@Autowired` en campos.
- `@Transactional(readOnly = true)` en las lecturas, `@Transactional` en las escrituras.
- La anotación `@Service` no es decorativa: el pointcut de `TenantFilterAspect` matchea
  por ella. **Un service sin `@Service` no activa el filtro de tenant.**

## Entidades

- Las tenant-scoped extienden `BaseTenantEntity` y suman `@Filter(name = "tenant")`.
- El `@FilterDef` global está en `model/package-info.java`. No duplicarlo.
- Lombok `@Getter` / `@Setter`. Evitar `@Data` en entidades JPA.
- Los enums del dominio viven en `model/` junto a las entidades: `EstadoAsistencia`,
  `MetodoAsistencia`, `DiaSemana`, `Rol`, `MotivoCargaManual`, etc.

## Migraciones

- Una migración aplicada **no se edita jamás**. Un cambio de esquema es un archivo nuevo
  `V0XX__descripcion.sql`.
- **Toda tabla se nombra `${esquema}.tabla`, incluidas las referenciadas por una FK.**
  El historial de Flyway vive en `asistenciautomatica_meta`, y `default-schema` no solo
  dice dónde va ese historial: es también el esquema por defecto de la conexión mientras
  se migra. Un `CREATE TABLE` sin calificar se crea en la base del historial.

  El síntoma engaña: llega como `errno 150, foreign key incorrectly formed`, porque las
  tablas referenciadas no están en el esquema donde se está creando. V001 a V014 no lo
  sufrieron porque se aplicaron antes de que el historial se mudara de base; V015 fue la
  primera escrita después y ahí apareció.

  ⚠ Eso dejaba el esquema **no reproducible desde cero**: una instalación nueva creaba las
  16 tablas dentro de la base del historial y V015 fallaba. Se descubrió al intentar
  reconstruir la base de cero, no antes, porque los tests corren sobre H2, donde no existe
  la separación en dos esquemas. Resuelto por el consolidado, abajo.
- **El esquema vive en una sola migración consolidada.** `db/migration/` contiene
  únicamente `V001__esquema_consolidado.sql`, que crea las 16 tablas calificadas y en orden
  de dependencias, más los catálogos de `roles` y `motivos_carga_manual`. Las quince
  migraciones originales se conservan sin cambios en `db/historico/`, fuera de
  `spring.flyway.locations`: son referencia, no se aplican.
- **Una instalación ya migrada no debe aplicar el consolidado.** Se le borra la tabla
  `flyway_schema_history` y `spring.flyway.baseline-on-migrate=true` la marca en la versión
  1 al arrancar, sin tocar los datos. Verificado: 294 filas intactas después del baseline.
- Cuando una migración falla, Flyway deja la fila en `flyway_schema_history` con
  `success = 0` y **la aplicación no vuelve a arrancar hasta limpiarla**. Se borra esa
  fila y se deshace a mano lo que el script haya alcanzado a aplicar: el DDL de MariaDB
  no es transaccional, así que puede haber quedado a medias.

## Rutas HTTP

Patrón consistente en todos los CRUD:

```
GET   /entidades              listado
GET   /entidades/nueva        formulario de alta
POST  /entidades/nueva        procesar alta
GET   /entidades/{id}/editar  formulario de edición
POST  /entidades/{id}/editar  procesar edición
POST  /entidades/{id}/baja    baja lógica
POST  /entidades/{id}/alta    reactivación
```

En español, plural, minúscula. Las acciones destructivas siempre por `POST`, nunca por
`GET`, y con CSRF activo.

## Frontend

- Thymeleaf server-side. El JavaScript es puntual, no hay framework.
- Un solo `main.css`. Modo oscuro por defecto y claro conmutable (`tema.js`).
- El JS está agrupado en tres carpetas bajo `static/js/`: `comun/` (los transversales),
  `academico/` y `facial/` (los dos que manejan cámara). `SecurityConfig` autoriza
  `/js/**`, así que una carpeta nueva no necesita tocar seguridad.
- Componentes JS reutilizables ya existentes, todos en `comun/`: toast, modal de
  confirmación, detección de overflow del navbar, mostrar/ocultar contraseña, select
  buscable, filtro de tabla. **Reutilizarlos antes de escribir uno nuevo.**
- Las cinco pantallas de `auth/` no extienden `layout/base.html`: cargan sus scripts por
  su cuenta. No es carga duplicada, y si se agrega un script global hay que sumarlo ahí
  también.
- **Nunca escribas un color ni una duración a mano: salen de los tokens de `:root`.**
  Un valor suelto se ve bien en oscuro y se rompe en claro, que es exactamente lo que
  pasó con los badges —el de INSTITUCIÓN llegó a 1.69:1 sobre fondo claro—.
  - Color de estado sobre fondo teñido (badges, alerts, toasts): `--tinte-*` para el
    fondo y `--sobre-tinte-*` para el texto. **No** `--success`/`--danger` sueltos: esos
    están calibrados como texto sobre el fondo de la página, no sobre su propio tinte.
  - Movimiento: `--t-rapido` (respuesta al puntero), `--t-base` (algo aparece o
    desaparece), `--t-lento` (algo recorre distancia), siempre con `--ease`.
  - Si un color tiene que ir a un `<canvas>`, se lee con `getComputedStyle` en vez de
    repetir el hex (`tokenColor()` en `pase-asistencia.js`).
- **Formularios: el envío bloquea el botón.** Lo hace `comun/envio-form.js` solo. Para
  que el botón cambie de texto mientras viaja, agregale `data-texto-enviando="Guardando..."`.
- **El modo del navbar se decide en un script inline, no en `navbar.js`.** Está apenas
  cerrado el `<header>` y es síncrono a propósito: mide si los enlaces entran y aplica
  `nav-compact` antes del primer paint. **No lo muevas a un script con `defer`**: ese corre
  en `DOMContentLoaded` y el navegador puede haber pintado antes, con lo que la página
  aparece un instante con el menú entero desplegado y después salta al drawer cerrado. Es
  una carrera, así que se ve de a ratos y sobre todo en máquinas lentas. Hubo una versión
  que lo resolvía cacheando el modo en `sessionStorage`; fallaba en la primera carga de la
  sesión, con el modo de una ventana ancha guardado, y en modo privado.
- **El modo tarjeta de las tablas es opt-in: `class="table table--tarjetas"`.** Sin esa
  clase la tabla se sigue desplazando de costado en pantalla angosta, que es lo correcto
  para las que no se adaptaron: no tienen `data-label` y se verían como valores sin nombre.
- **Adaptación a móvil: sí, con límites** (RNF-23 reinterpretado, ADR-0015). Asistencias,
  reportes y consulta de docentes se adaptan. La carga académica y la grilla se quedan en
  escritorio. El pase y el registro del rostro están **bloqueados** fuera de un puesto
  autorizado, y el bloqueo es por equipo, nunca por ancho de pantalla.

## Tests

- JUnit 5. Unitarios con Mockito, integración con `@SpringBootTest` sobre H2 en memoria
  y `@ActiveProfiles("test")`.
- **Los dos tipos viven separados y se corren por separado.** Los unitarios se llaman
  `*Test` y van en la carpeta de la capa que prueban. Los de integración se llaman `*IT`
  y van todos en `integracion/`, sin importar qué capa ejerciten: lo que los define es
  que levantan el contexto, no dónde pega el flujo que prueban.

  | Comando | Qué corre |
  |---|---|
  | `./gradlew test` | solo los `*Test`, sin levantar Spring |
  | `./gradlew integrationTest` | solo los `*IT` |
  | `./gradlew build` | ambos — `check` depende de las dos tareas |

  ⚠ Ese `dependsOn` de `check` en `build.gradle` es lo único que mantiene los `*IT`
  dentro de la build. Si se toca, la build sigue en verde sin ejecutarlos y nadie se
  entera: la misma forma de fallo que TD-007.
- `application-test.properties` va en `src/test/resources/`, no en `src/main/resources/`.
  Ahí se empaquetaba dentro del JAR de producción.
- **Los unitarios con Mockito no tocan Hibernate ni el tejido AOP.** Por eso la fuga
  multi-tenant de TD-007 pasó desapercibida con 107 tests en verde. Todo lo que dependa
  del filtro de tenant, de una query real o de la cadena de seguridad necesita un test
  de integración.
- **Verificación por mutación:** cuando un test cubre un bug corregido, reintroducir el
  bug y comprobar que el test efectivamente falla. Un test que pasa igual con y sin el
  bug no prueba nada.
- El CI corre `./gradlew build` completo sobre cada push y PR a `main`.

## Git

- Rama única: `main`.
- Commits en español, en presente, con prefijo de tipo: `fix:`, `feat:`, `docs:`,
  `refactor:`, `test:`.
- Los hitos se marcan con tags (`sprint-N-cierre`, `v1.0.0`).
- `CHANGELOG.md` se actualiza en los hitos, no en cada commit.

## Qué no entra al repositorio

El repositorio es **público**. Nunca se commitean:

- `application-local.properties` (credenciales de XAMPP).
- `credenciales-locales.txt`, `credenciales_proyecto.txt`.
- **Una credencial real dentro de un test.** El `.gitignore` cubre archivos, no constantes:
  `MigracionesIT` tuvo la contraseña de la base escrita en claro y versionada durante once
  commits, mientras el properties que la contenía estaba correctamente ignorado. Un test que
  necesite conectarse a la base lee las credenciales de `application-local.properties` o de
  las variables `MARIADB_USER` / `MARIADB_PASSWORD`, y si no las encuentra **se saltea**.
  Una clave inventada para el test —`"test-clave-dev"`— no es un secreto y puede ir escrita.
- Datos de prueba con información personal — los del CENT 35 son ficticios y así deben
  quedar.
- Modelos faciales, `.onnx`, `.pb`, `.caffemodel`, `.env`.
- La carpeta `docs/`, que está gitignoreada de forma deliberada.

Una contraseña que entra al historial de git queda ahí para siempre: borrarla después no
sirve, porque sigue estando en los commits anteriores.

**El `.gitignore` cuida el repositorio, no el empaquetado.** `application-local.properties`
tiene que vivir en `src/main/resources/` para que `bootRun` lo encuentre, y de ahí se
copiaba dentro del JAR: el repositorio quedaba limpio mientras las credenciales viajaban en
el artefacto que se entrega. Por eso `build.gradle` las excluye con
`tasks.withType(Jar).configureEach`, y no solo de `bootJar`: el plugin de Spring Boot genera
además un `-plain.jar` con la tarea `jar`, y excluirlo de uno solo dejaba el otro abierto.
`bootRun` no se ve afectado porque lee de `build/resources/main`, no del JAR.

## Al refactorizar la estructura de paquetes

Auditar explícitamente todo lo que referencia rutas de paquete **como texto**: pointcuts
de AOP, `@ComponentScan`, escaneo de entidades, reglas de ArchUnit. El compilador no
avisa y los tests tampoco. Es exactamente lo que pasó en TD-007.
