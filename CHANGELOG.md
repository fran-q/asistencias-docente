# Changelog

Highlights de cada sprint del proyecto, en orden cronológico inverso.

> Formato basado en [Keep a Changelog](https://keepachangelog.com/) y siguiendo
> los tags `sprint-N-cierre` del repositorio.

---

## Marca de salida y bloques de presencia

**Período:** agosto–septiembre de 2026.

Hasta acá el sistema registraba un solo evento por clase: el docente pasaba por la
cámara al llegar y eso era todo. Podía decir que vino, pero no que dictó la clase.
Ahora también se registra la salida, y con eso el reporte pasa a poder responder
cuánto tiempo estuvo cada docente frente a sus cursos.

La restricción que le dio forma a todo el diseño fue la otra mitad del pedido: un
docente con clases encadenadas **no** tiene que registrarse una vez por clase. Una
entrada y una salida cubren la jornada entera.

### Agregado

- **Bloque de presencia (ADR-0017, V019).** La unidad de la que se predican una entrada
  y una salida deja de ser el horario y pasa a ser el lapso continuo en que la persona
  estuvo en la institución. Un bloque agrupa las clases separadas por menos que el
  umbral de la institución, sin importar de qué materia sean.

  `asistencias` **no cambió de significado**: sigue siendo una fila por (docente,
  horario, fecha) y solo suma a qué bloque pertenece. Reportes, justificaciones y carga
  manual siguen operando igual.

  Efecto lateral favorable: un docente con cuatro clases encadenadas deja **dos**
  evidencias biométricas por jornada, no ocho. Menos dato sensible tratado para el mismo
  resultado.

- **El sentido de la marca se deduce, no se elige.** Sin bloque abierto, la pasada por
  la cámara es una entrada; con bloque abierto y diez minutos cumplidos, es una salida.
  No hay selector en la pantalla: el docente es sujeto pasivo por diseño, y un modo mal
  seleccionado produce un dato incorrecto que parece correcto.

- **Cierre automático de lo que nadie cerró (RF-79, RF-80).** El job de ausencias pasó a
  cerrar el día: primero cierra los bloques sin salida —imputando las clases que el
  docente cubrió— y recién después genera las ausencias. La asistencia **no queda rehén**
  del dato de salida; lo que queda pendiente es la hora, marcada como presumida y
  anunciada en el panel de inicio.

- **Cierre manual y corrección (RF-83, V020).** Con el algoritmo actual el reconocimiento
  puede fallar al salir por un cambio de iluminación, así que el cierre a mano es parte
  del flujo normal y no un caso de borde. Queda asentado quién lo hizo y por qué, con el
  mismo catálogo de motivos que la carga manual.

- **Horas dictadas en los reportes.** Pantalla, CSV y PDF muestran los minutos
  efectivamente cubiertos contra los programados. Son la **intersección** entre la
  permanencia y la franja de la clase: contar la permanencia entera en cada una haría
  que un docente con tres clases seguidas figurara dictando el triple.

### Cambiado

- **La tolerancia pasó a ser bidireccional (ADR-0018).** Perdona hacia los dos lados del
  horario y con el mismo número de minutos: PRESENTE hasta `hora_inicio + tolerancia`, y
  salida en hora desde `hora_fin - tolerancia`.

  ⚠ **Cambia cómo se clasifica al personal.** Con tolerancia 15, quien llega 18:05 pasa
  de TARDE a PRESENTE. El histórico **no se recalcula**: dos registros iguales pueden
  tener estados distintos según de qué lado del cambio caigan, y eso es un cambio de
  criterio, no un error. Conviene avisarle al equipo administrativo antes de que lo vea
  en un reporte.

  El motivo del cambio: el RF-19, el glosario y el javadoc de `Horario` describían la
  tolerancia como gracia posterior al inicio, y el código hacía lo contrario desde la
  decisión 1 de ADR-0008. La contradicción llevaba tiempo conviviendo y no se detectaba
  porque el único test de clasificación usaba un caso que da TARDE con las dos lecturas.

- **El pase distingue entrada de salida de un vistazo.** Verde con «ENTRA · Nombre», azul
  con «SALE · Nombre». El azul quedó libre porque «ya estaba marcado» dejó de existir en
  el flujo normal: con bloques, la segunda pasada es la salida.

- **El listado del día y los reportes** separan la columna de hora en entrada y salida, y
  marcan las horas que completó el sistema.

### Corregido

- **Revocar el consentimiento no dejaba de usar el rostro.** Marcaba el consentimiento
  como no vigente pero no tocaba el modelo facial, y el pase compara contra los modelos
  *activos* sin mirar el consentimiento: un docente que revocaba **seguía siendo
  reconocido y seguía recibiendo marcas automáticas**. Era una violación de la regla que
  el propio ADR-0005 establece —sin consentimiento vigente no se registra *ni se usa* un
  rostro— y del RNF-13.

  Ahora la revocación da de baja el modelo activo y lo evicta del cache, en ese orden.
  Sigue siendo baja lógica: revocar es oposición, no cancelación. Volver a otorgar el
  consentimiento **no** reactiva el modelo — hay que registrar el rostro de nuevo.

- **Un pedido de recuperación que no emitía código no se distinguía de un canal roto.**
  Cuando el identificador no era de nadie, el flujo cortaba antes de generar nada y no
  quedaba rastro en ninguna parte: la pantalla contesta lo mismo exista o no la cuenta
  —eso es a propósito, ADR-0009— y la terminal tampoco decía nada. Probando el flujo no
  había forma de saber si la cuenta no existía o si el canal estaba caído.

  El canal de consola ahora informa por qué no salió ningún código, y distingue los
  cuatro motivos que antes eran uno solo: no existe, el usuario está repetido en varias
  instituciones, la cuenta está dada de baja, o existe pero la emisión falló (límite de
  reenvíos, SMTP caído). **La respuesta HTTP no cambió**: el aviso sale por consola, que
  en desarrollo ya muestra los códigos en claro. El canal de correo calla, porque avisar
  ahí sería exactamente revelar lo que la decisión oculta.

- **La ventana de la clase daba la vuelta al reloj.** Restarle la tolerancia a una clase
  que empieza 00:10 daba las 23:55 del día anterior y dejaba la ventana invertida, con lo
  que esa clase no aceptaba ninguna marca. Ahora se topea en el borde del día.

---

## Identidad de la persona, separada de la cuenta y del vínculo docente

**Período:** agosto de 2026.

El sistema tenía dos representaciones de personas que no se conocían entre sí: `usuarios`
guardaba nombre, apellido y correo, y `docentes` guardaba eso mismo más DNI, legajo y
teléfono. Ningún vínculo entre las dos. Mientras el equipo administrativo y el cuerpo
docente fueran conjuntos disjuntos no molestaba, y en un instituto terciario **no lo son**:
es habitual que un coordinador o un secretario académico también dé clases.

Los dos problemas que realmente empujaron el cambio son de trazabilidad. El histórico
laboral no se podía representar —un `UNIQUE (institucion_id, dni)` impedía una segunda
fila para quien se fue y volvió—, y un pedido de acceso o supresión de la Ley 25.326 solo
se podía responder cruzando tablas por nombre o por correo, que es heurístico. Para un
sistema que trata datos biométricos, la diferencia entre *puedo responder* y *puedo
responder y demostrar que la respuesta es completa* es sustantiva.

### Agregado

- **Tabla `personas` (ADR-0016, V016).** La identidad —nombre, apellido, DNI, correo,
  teléfono— vive en un solo lugar. `usuarios` y `docentes` dejan de tenerla y pasan a
  apuntar a la persona: una cuenta es un acceso, un docente es un vínculo laboral, y la
  persona es la que persiste a los dos. Dar de baja un docente ya no borra a la persona,
  que es lo que hace posible responderle a un inspector desde cuándo y hasta cuándo
  trabajó alguien.

- **Registro de cambios de identidad (V017).** `cambios_identidad` guarda una fila por
  campo modificado, con el valor anterior, el nuevo, quién lo hizo y cuándo. Corregir un
  apellido mal tipeado y cambiarlo porque la persona se casó se ven igual en la tabla
  final; acá quedan distinguibles.

- **Quién dio de baja, en ocho tablas (V017).** Columna `dado_de_baja_por` con
  `ON DELETE SET NULL`: la baja lógica ya decía *cuándo*, ahora también dice *quién*.

- **Confirmación antes de pisar una identidad.** Editar los datos de una persona alcanza
  a todos sus roles a la vez. Antes de guardar se muestra el impacto —qué cambia, y sobre
  qué cuenta y qué vínculo docente— y se pide confirmación explícita. Lo mismo al dar de
  alta un docente cuyo DNI ya existe: en vez de rechazarlo o duplicarlo, se ofrece el
  reingreso sobre la misma persona.

### Cambiado

- **Baseline consolidado del esquema (V001).** Las quince migraciones originales pasaron
  a `db/historico/` y el esquema arranca de una sola, `V001__esquema_consolidado.sql`.

  El motivo no fue estético: **el esquema no se podía reproducir desde cero.** Las
  migraciones viejas no calificaban las tablas con el esquema destino, así que las creaban
  en la base del historial de Flyway y la siguiente fallaba. El CI no lo detectaba porque
  corre sobre H2, donde el problema no aparece. Se verificó levantando el esquema entero
  desde cero contra MariaDB.

- **El alta de institución dejó de pedir nombre y apellido.** Una institución no es una
  persona física: el formulario pide el nombre oficial del establecimiento y su CUIT, y
  la cuenta de acceso se describe como lo que es —el acceso del establecimiento, no el de
  alguien—. `usuarios.persona_id` pasó a admitir NULL para que esa cuenta pueda existir
  sin una persona detrás (V018), y los listados de usuarios pasaron a `LEFT JOIN`: con un
  `JOIN` común, la única cuenta que administra el sistema desaparecía de la pantalla que
  administra las cuentas.

### Corregido

- **Había una credencial de MariaDB escrita en el test de migraciones.** Nunca llegó a
  publicarse. La contraseña se rotó y el test pasó a leerla de `application-local.properties`
  o de las variables `MARIADB_USER` y `MARIADB_PASSWORD`.

---

## Acceso móvil, puestos de captura y revisión de interfaz

**Período:** agosto de 2026.

Se abre el uso desde el teléfono para la gestión diaria —listados, reportes,
justificaciones— y, como contrapartida, la captura biométrica queda restringida a
equipos que la institución registra. Lo segundo no es un accesorio de lo primero:
es lo que permite lo primero sin repartir el tratamiento de datos sensibles entre
dispositivos personales.

En el camino, una revisión de la interfaz contra las heurísticas de Nielsen dejó a
la vista varias cosas que estaban rotas y no se notaban.

### Agregado

- **Puestos de captura (ADR-0015, V015).** El pase de asistencia y el registro del
  rostro solo funcionan desde un equipo designado. El equipo se identifica con una
  cookie cuyo token se guarda hasheado, se autoriza desde la propia máquina —no hay
  forma de habilitar otra a distancia— y se revoca desde la pantalla de gestión, con
  efecto en la petición siguiente.

  El control es contra el **equipo**, no contra el tamaño de la pantalla: el ancho de
  la ventana no dice qué máquina es, y por ancho la secretaria quedaría bloqueada al
  angostar el navegador mientras una tablet en horizontal pasaría igual.

  ⚠ **Puesta en marcha:** la migración no siembra ningún puesto, ni siquiera para los
  datos de prueba —sería dejar un token escrito en un repositorio público—. Después de
  aplicar V015 **nadie puede tomar asistencia hasta designar el primer puesto** desde
  la aplicación.

- **Acceso móvil a la gestión.** Listado del día, reportes y docentes muestran una
  tarjeta por fila debajo de 760 px, en vez de una tabla que obligaba a desplazarse el
  doble del ancho de un teléfono. Es opt-in (`table--tarjetas`): las pantallas de carga
  académica siguen siendo de escritorio, y sin la clase la tabla conserva su
  desplazamiento lateral.

  Las celdas declaran `role` explícito porque cambiar el `display` de una tabla hace
  que Chrome y Safari le quiten la semántica de tabla al árbol de accesibilidad.

- **El botón se bloquea mientras el formulario viaja.** Entre el clic en "Guardar" y
  la recarga no había ninguna señal propia, y nada impedía volver a apretar y mandar
  el alta dos veces.

- **Escala de movimiento y respeto por `prefers-reduced-motion`.** Convivían seis
  duraciones sueltas y tres criterios de easing; ahora son tres escalones y un único
  easing. El bloque de movimiento reducido no existía: se reduce a 1 ms en vez de
  anular las transiciones, para que el código que espera `transitionend` las siga
  recibiendo.

### Corregido

- **El parpadeo del navbar seguía ahí.** El post-cierre lo dio por corregido cacheando
  el modo en `sessionStorage`, pero eso fallaba en la primera carga de la sesión —el
  valor todavía no existe—, cuando quedaba guardado el modo de una ventana ancha, y en
  modo privado. En esos casos la página se pintaba con el menú entero desplegado y
  después saltaba al drawer cerrado.

  Es una carrera entre el primer paint y `navbar.js`, que carga con `defer`: se midió
  primer paint a 336 ms contra `DOMContentLoaded` a 366 ms en una carga, y al revés en
  la siguiente. Por eso se veía de a ratos y sobre todo en máquinas lentas. Ahora el
  modo se mide en un script inline síncrono apenas cerrado el `<header>`, y
  `sessionStorage` se eliminó.

- **Los badges eran ilegibles en tema claro.** Medidos contra su propio fondo:
  `INSTITUCIÓN` en 1,43:1, `ADMINISTRADOR` en 2,53:1 y `ACTIVO` en 2,58:1, todos muy
  por debajo del 4,5:1 que el proyecto se exige. Toda la familia de colores de estado
  estaba escrita como valores fijos calibrados para fondo oscuro, así que el tema claro
  nunca los tocó. Ahora son tokens por tema y pasan entre 4,7:1 y 5,7:1. La medición
  destapó de paso el badge neutro en tema oscuro, que daba 4,29:1.

- **El pase pintaba sus estados con colores de otra paleta.** Estaban como estilo
  inline en el JavaScript, así que el tema claro no podía corregirlos y el verde del
  éxito quedaba lavado sobre fondo blanco. El recuadro del canvas ahora lee los tokens.

- **Seis títulos de página decían "Asistencias" en vez de "Visum".** Los seis eran
  dinámicos, y quedaron fuera del renombre.

- **Las tablas no avisaban que seguían hacia el costado.** El desplazamiento existía,
  pero la columna de acciones cortada al medio se leía como un error de maquetado. Un
  degradado marca el borde que tiene contenido detrás.

- **Toda migración nueva se creaba en el esquema equivocado.** Cuando el historial de
  Flyway se mudó a `asistenciautomatica_meta`, ese esquema pasó a ser también el de la
  conexión durante la migración, así que un `CREATE TABLE` sin calificar caía ahí. Las
  V001–V014 no lo sufrieron porque se aplicaron antes del cambio; V015 fue la primera
  escrita después. El síntoma engañaba: llegaba como `errno 150, foreign key
  incorrectly formed`. Las tablas se nombran `${esquema}.tabla`.

### Cambiado

- **Limpieza estructural del código.** Las anotaciones de Bean Validation y sus
  `ConstraintValidator` salen de `dto/` a una capa `validacion/` propia; el estado que
  vive en la sesión HTTP (`AltaPendiente`, `ConfirmacionIdentidad`) pasa de `service/`
  a `dto/`, que es lo que son; y `DestinosDelMenuAdvice` se mueve de `config/` a
  `controller/`, junto al resto de los `@ControllerAdvice`. El JavaScript queda
  agrupado en `comun/`, `academico/` y `facial/`.

- **RNF-23 reinterpretado.** Estaba cerrado como "sin adaptación a móvil, por decisión"
  y figuraba en la lista de cosas fuera del rumbo. Pasa a: escritorio para la captura,
  móvil para la gestión y la consulta.

### Verificación

357 tests, sin fallos ni salteados. Lo que se agregó en esta etapa:

- Aislamiento entre instituciones de los puestos, **verificado por mutación**: al
  quitar el filtro explícito de la consulta, el caso por HTTP siguió pasando —el filtro
  de Hibernate tapaba la fuga— y solo lo detectó el caso a nivel de servicio, donde no
  hay tenant en contexto. Sin ese caso, alguien podría dar por redundante el filtro
  explícito con la suite en verde, que es la forma exacta que tuvo TD-007.
- Que la supresión de datos biométricos **no** quede alcanzada por el bloqueo: comparte
  prefijo con el registro del rostro, y condicionar un derecho ARCO a estar frente a una
  máquina determinada sería ponerle una traba.
- Que ninguna tabla sin etiquetas entre en modo tarjeta. La primera versión del cambio
  aplicaba a `.table` a secas y se coló en los cinco listados no adaptados, donde
  aparecían valores sin nombre.

---

## Post-cierre — Revisión, endurecimiento y verificación de correo

**Período:** julio de 2026, posterior al cierre de los sprints.

Etapa de revisión sobre el sistema ya terminado. No agrega alcance funcional
planificado: corrige lo que la revisión encontró y cierra huecos que el
relevamiento original no había contemplado.

### Corregido

- **Fuga multi-tenant (TD-007).** El pointcut del aspecto que activa el filtro de
  Hibernate apuntaba a `..application..`, un paquete que dejó de existir con la
  reorganización a package-by-layer. El aspecto quedó **silenciosamente inactivo**
  y los listados devolvían datos de todas las instituciones. Ahora el pointcut va
  por la anotación `@Service`, para que un futuro renombre de paquetes no lo
  vuelva a romper.
- **El reporte mostraba el día equivocado.** La columna que acompaña a la fecha se
  tomaba del día programado del horario en vez de la fecha de la marca. Con ambos
  coincidiendo era invisible; al divergir, una asistencia del sábado figuraba como
  lunes. Afectaba también al CSV exportado.
- **Faltaba validar la fecha en la carga manual.** Nada impedía registrar una clase
  de lunes con fecha de sábado, porque ahí la fecha la elige el administrador a
  mano.
- **`usuarios.ultimo_login` nunca se escribía.** La columna existía desde la
  primera migración y se mostraba en pantalla, pero ningún código la actualizaba.
- **Parpadeo del navbar al navegar.** Cada página se pintaba primero en modo
  escritorio y recién después colapsaba a menú hamburguesa.

### Agregado

- **Verificación de correo y recuperación de contraseña** mediante código de un
  solo uso enviado por mail (ADR-0009). Antes, si un administrador olvidaba la
  contraseña dependía del superadmin, y si la olvidaba el superadmin la
  institución quedaba sin acceso a su cuenta de gestión.
- **Tests de integración reales**, que es lo que faltaba para que la fuga
  multi-tenant no hubiera pasado desapercibida: toda la suite era unitaria con
  Mockito, que nunca toca Hibernate.
  - Aislamiento entre instituciones ejercitando el filtro de verdad.
  - Reglas de rol y CSRF sobre peticiones HTTP.
  - Ambos verificados por mutación: se reintrodujo cada falla y se comprobó que
    los tests la detectan.
- **Encoding UTF-8 fijado en la compilación**, para que el build no dependa del
  locale de la máquina.

### Cambiado

- **Comentarios unificados en los 128 archivos fuente**: una cabecera de dos
  oraciones por archivo y una línea por función. En el camino aparecieron
  comentarios que afirmaban cosas falsas, como que el filtro de tenant "se
  activará en la Fase B" cuando ya estaba activo.
- **Documentación consolidada** en `docs/`, en carpetas numeradas por tipo. Los
  archivos de cátedra estaban duplicados byte por byte en dos ubicaciones.
- Tests: de 107 a 150.

---

## Sprint 6 — Cierre del proyecto (sprint-6-cierre)

**Período:** 19 al 24 de junio de 2026.

### Agregado
- **Reportes con exportación a CSV** (`/reportes`). Filtros por rango de fechas,
  docente, materia, estado y método. Descarga UTF-8 con BOM para que abra
  directo en Excel.
- **Diagramas UML** (`docs/5-diagramas/`, migrados a Mermaid en julio 2026):
  - Casos de uso.
  - Clases del dominio.
  - Secuencia del pase de asistencia.
- **Manuales** en Markdown (`docs/6-manuales/`):
  - Manual del administrador (cómo usar el sistema).
  - Manual técnico (instalación, configuración, backup, troubleshooting).
- Tests del `ReporteAsistenciaService`.
- TD-004, TD-005 y TD-006 documentados en `TECH_DEBT.md`.
- README final con resumen ejecutivo.

---

## Sprint 5 — Asistencia automática end-to-end (sprint-5-cierre)

**Período:** 12 al 18 de junio de 2026.
**ADR:** [0008 - Asistencia automática](docs/4-arquitectura/adr/0008-asistencia-automatica.md).

### Agregado
- **Pase de asistencia** (`/asistencia/pase`): la pantalla del reconocimiento
  reemplaza el "modo prueba" y ahora **marca asistencia** al reconocer:
  - Recuadro verde cuando se marca PRESENTE/TARDE.
  - Recuadro azul cuando "ya estaba marcado" (idempotente).
  - Recuadro amarillo cuando hay rostro pero no hay clase en curso.
  - Recuadro rojo cuando hay rostro pero no se reconoce.
  - **Pausa de 5 segundos** tras cada marca exitosa (evita ruido).
- **Listado** `/asistencias` con filtros (fecha, estado, docente). Incluye
  **AUSENTES calculadas** al vuelo para horarios cuyo `hora_fin` ya pasó y
  no tienen marca.
- **Carga manual** de asistencia (`/asistencias/manual/nueva`) con catálogo
  de motivos (FALLA_CAMARA, FALLA_RECONOCIMIENTO, NO_REGISTRADO, OTRO).
- **Justificación de ausencias** (`/asistencias/{id}/justificar`) con
  motivo libre + URL de documento opcional.
- Tabla `asistencias`, `asistencias_manuales`, `motivos_carga_manual`,
  `justificaciones_ausencia` (todas ya estaban diseñadas en V001 desde el
  Sprint 0; Sprint 5 les pone la lógica encima).

### Decisiones técnicas
- **PRESENTE vs TARDE**: la tolerancia es **por horario** (`tolerancia_min`
  en cada `Horario`). Antes del `hora_inicio` (dentro de la tolerancia)
  → PRESENTE; después del `hora_inicio` → TARDE.
- **AUSENTE no se persiste** desde el flujo automático: se calcula al
  listar. *(Cambiado más adelante: el job de generación de ausencias las
  materializa, y el cálculo al vuelo quedó cubriendo solo la ventana entre
  el fin de la clase y la corrida siguiente.)*
- **Idempotencia tres-niveles**: UNIQUE en BD + verificación en service +
  pausa en frontend.
- **Defensa contra race condition**: `saveAndFlush` + catch de
  `DataIntegrityViolationException` para releer y devolver `yaEstaba`.
- Conversión lineal **distancia LBPH → confianza 0-1** (TD-004).

---

## Sprint 4 — Reconocimiento facial (sprint-4-cierre)

**Período:** 29 de mayo al 11 de junio de 2026.
**ADR:** [0007 - Reconocimiento facial con JavaCV + LBPH](docs/4-arquitectura/adr/0007-reconocimiento-facial-lbph.md).

### Agregado
- **JavaCV 1.5.11 + OpenCV 4.10.0** integrados al build.
- `gradle.properties` con `javacpp.platform=windows-x86_64` para reducir
  la descarga de binarios nativos.
- **Registro del modelo facial** del docente (`/docentes/{id}/rostro/registrar`):
  grabación de 30 s con webcam, entrenamiento LBPH local, cifrado AES-256-GCM
  (Spring Security Crypto), compresión gzip antes de cifrar.
- **Identificación en vivo** (`/reconocimiento/prueba` en Sprint 4, reemplazada
  en Sprint 5): loop continuo, cache de recognizers en memoria.
- V005 (auditoría forense de consentimientos) y **V006** (`embedding_cifrado`
  pasa de `BLOB` a `LONGBLOB`).
- Tests de `CifradoBiometricoService`, `ModeloFacialService` e `IdentificacionFacialService`.

### Decisiones técnicas
- **LBPH por docente** (cada modelo entrenado con varias capturas de UNA
  persona) — encaja con la tabla `modelos_faciales`.
- **gzip antes de cifrar** — el YAML de OpenCV es altamente repetitivo,
  comprime 5-10x. Sin esto el INSERT supera `max_allowed_packet` de
  MariaDB y corrompe tablas del sistema (lección aprendida durante el
  sprint).
- `@JdbcTypeCode(SqlTypes.LONGVARBINARY)` en el `byte[]` para que Hibernate
  6 espere LONGBLOB.
- Cache en memoria de recognizers (TD-005).

---

## Sprint 3.5 — Reorganización a package-by-layer

**Período:** dentro del Sprint 3, post-cierre del Sprint 3.
**ADR:** [0006 - Package by layer](docs/4-arquitectura/adr/0006-organizacion-por-capas.md).

A pedido del docente de Prácticas Profesionalizantes III, se migra el código
de **package-by-feature** (ADR-0001) a **package-by-layer**:
`controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`.

47 archivos `.java` movidos con `git mv` (preservando historial). 38
`package-info.java` eliminados; uno nuevo creado en `model/` con el
`@FilterDef("tenant")`.

ADR-0001 marcada como reemplazada por ADR-0006.

---

## Sprint 3 — Docentes + Consentimiento biométrico (sprint-3-cierre)

**Período:** 7 al 28 de mayo de 2026.
**ADR:** [0005 - Consentimiento biométrico](docs/4-arquitectura/adr/0005-consentimiento-biometrico.md).

### Agregado
- CRUD completo de **Docentes** (`/docentes`).
- Asignación de **docente titular** a Materia (`Materia.docenteTitular`).
- Asignación de **docente asignado** a Comisión (`Comision.docenteAsignado`).
- **Consentimiento biométrico** (Ley 25.326 + AAIP 255/2022):
  - Texto legal versionado (`TextoConsentimiento.VERSION_ACTUAL = "2026-05-v1"`).
  - Otorgamiento / revocación con auditoría forense (IP, User-Agent).
  - Estados: NUNCA_OTORGADO, ACTIVO, REVOCADO (calculado).
  - Badges en la ficha del docente y columna en el listado.
- V004 (docente_asignado_id nullable durante la transición) y V005
  (columnas de auditoría forense en consentimientos).
- Pantallas de error custom (`/error.html`).

---

## Sprint 2 — Académico + grilla semanal (sprint-2-cierre)

**Período:** 5 al 6 de mayo de 2026.
**ADR:** [0004 - Defensa en JOINs](docs/4-arquitectura/adr/0004-tenant-filter-en-joins.md).

### Agregado
- CRUD de **Carreras**, **Materias**, **Comisiones**, **Horarios**.
- **Grilla semanal** por carrera (CSS Grid con posiciones pre-calculadas).
- Validación de superposición de horarios por comisión.
- Fix de fuga multi-tenant en JPQL con JOINs (TD-003).
- Componentes JS reutilizables: toast, confirm-modal, navbar overflow
  detection, password show/hide con SVG.

---

## Sprint 1 — Multi-tenancy + autenticación real + CRUDs (sprint-1-cierre)

**Período:** 1 al 4 de mayo de 2026.

### Agregado
- **Multi-tenancy por discriminator** (`institucion_id`) con
  `@Filter("tenant")` Hibernate y `TenantContext` ThreadLocal.
- **Autenticación real** con Spring Security, BCrypt, sesión HTTP cookie
  clásica (ADR-0003).
- CRUD de **Mi Institución** y **Usuarios administradores**.
- Roles `INSTITUCION` y `ADMIN`.
- UX: validación de formularios con Bean Validation, mensajes en español,
  ñ y tildes correctos.
- V003 (rename `SUPERADMIN_INSTITUCION` → `INSTITUCION`).

---

## Sprint 0 — Setup inicial (sprint-0-cierre)

**Período:** 24 al 30 de abril de 2026.
**ADR:** [0001 - Monolito Modular](docs/4-arquitectura/adr/0001-monolito-modular.md),
[0002 - Multi-tenancy por discriminator](docs/4-arquitectura/adr/0002-multi-tenant-discriminator.md).

### Agregado
- Setup Spring Boot 3.5 + Gradle + Java 21.
- MariaDB 10.4 vía XAMPP.
- Flyway con V001 (esquema completo de 16 tablas, con CHECK constraints).
- V002 con seed de datos de prueba.
- Login dummy en memoria (reemplazado en Sprint 1).
- CI GitHub Actions con build + tests.
- README inicial, diagrama BD, requerimientos.
