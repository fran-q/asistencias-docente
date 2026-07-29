# Manual del Administrador

**Sistema de Asistencias Digital con Reconocimiento Facial**
*Prácticas Profesionalizantes III — CENT35*

---

## Índice

1. [Acceso al sistema](#1-acceso-al-sistema)
2. [Roles](#2-roles)
3. [Mi institución](#3-mi-institución)
4. [Usuarios administradores](#4-usuarios-administradores)
5. [Carreras, materias, comisiones y horarios](#5-carreras-materias-comisiones-y-horarios)
6. [Docentes](#6-docentes)
7. [Consentimiento biométrico](#7-consentimiento-biométrico)
8. [Registro del rostro del docente](#8-registro-del-rostro-del-docente)
9. [Pase de asistencia automático](#9-pase-de-asistencia-automático)
10. [Listado de asistencias](#10-listado-de-asistencias)
11. [Carga manual de asistencia](#11-carga-manual-de-asistencia)
12. [Justificar una ausencia](#12-justificar-una-ausencia)
13. [Reportes y exportación CSV](#13-reportes-y-exportación-csv)
14. [Mi cuenta: verificar el correo y recuperar la contraseña](#14-mi-cuenta-verificar-el-correo-y-recuperar-la-contraseña)
15. [Buenas prácticas](#15-buenas-prácticas)

---

## 1. Acceso al sistema

1. Abrir el navegador (Chrome o Edge recomendados) en `http://localhost:8080`
   (o la URL donde esté desplegada la instalación).
2. Cargar **usuario y contraseña**. El sistema no permite recordar contraseñas
   ni copiar/pegar en el campo (seguridad RNF-06).
3. La primera vez, el sistema te va a llevar a **Mi cuenta** para que confirmes
   tu correo. Hasta que lo hagas no vas a poder usar el resto de las pantallas.
   El paso a paso está en la sección 14.
4. Si tu cuenta queda inactiva, la cuenta institucional puede reactivarla.

### Registrar una institución nueva

En el pie de la pantalla de login hay un enlace **"Registrar una institución
nueva"**. Es un paso de instalación, no algo de uso diario: pide una **clave de
instalación** que solo conoce quien puso el sistema en funcionamiento.

Al completarlo se crean de una sola vez la institución y su primera cuenta, que
es la que después da de alta al resto del personal. Esa primera cuenta **no
necesita confirmar el correo**: ya demostró tener la clave de instalación.

El nombre y el CUIT de la institución no se pueden repetir con los de otra ya
registrada.

> **Consejo**: la primera vez que ingreses, asegurate de que la cámara funcione
> en el navegador. El sistema te va a pedir permiso para usarla en la pantalla
> de **Pase de asistencia** y **Registrar rostro**.

---

## 2. Roles

| Rol | Qué puede hacer |
|---|---|
| **INSTITUCION** | TODO: editar datos de la institución, dar de alta/baja administradores, gestionar todo el académico y los docentes, pasar asistencia, ver reportes. |
| **ADMIN** | Operativo: gestionar carreras / materias / comisiones / horarios / docentes, pasar asistencia, ver listados y reportes. **No** puede tocar la configuración institucional ni crear otros usuarios. |

---

## 3. Mi institución

(Solo rol INSTITUCION)

Menú → **Mi Institución**.

Permite editar nombre, CUIT, dirección, email y teléfono de contacto.
Los cambios se guardan al hacer clic en **"Guardar cambios"**.

---

## 4. Usuarios administradores

(Solo rol INSTITUCION)

Menú → **Usuarios**.

- **Listado** con badge de estado (activo/inactivo).
- **+ Nuevo usuario**: completar username, email, contraseña, nombre, apellido y rol.
- **Editar**: actualizar datos. La contraseña se cambia desde el botón
  **"Cambiar contraseña"** en la edición.
- **Dar de baja**: marca al usuario como inactivo. La cuenta deja de poder
  loguearse pero no se elimina (mantiene historial).
- **Reactivar**: vuelve a habilitar la cuenta.

---

## 5. Carreras, materias, comisiones y horarios

Se administran como una jerarquía: **Carrera → Materia → Comisión → Horario**.

### 5.1 Carreras
Menú → **Carreras** → **+ Nueva carrera**.
- Código corto (ej. `INF`, `ECO`).
- Nombre completo.
- Activa/inactiva.

> Una carrera con materias activas no se puede dar de baja.

### 5.2 Materias
Menú → **Materias** → **+ Nueva materia**.
- Código (ej. `MAT-101`).
- Nombre.
- Carrera a la que pertenece.
- **Docente titular** (opcional): docente responsable académico de la materia.

### 5.3 Comisiones
Menú → **Comisiones** → **+ Nueva comisión**.
- Código corto (ej. `A`, `B`, `Mañana`).
- Materia.
- Cupo (opcional).
- **Docente asignado** (opcional): el que dicta efectivamente y a quien se
  le tomará asistencia.

### 5.4 Horarios
Menú → **Horarios** → **+ Nuevo horario**.
- Comisión.
- Día de la semana.
- Hora de inicio y fin.
- **Tolerancia (minutos)**: cuántos minutos antes del inicio se acepta una
  marca como PRESENTE. Pasado el horario de inicio, toda marca queda como
  TARDE (la hora exacta queda guardada).
- Vigencia desde / hasta (opcional).

### 5.5 Grilla semanal
Menú → **Grilla**. Vista calendar-like con los horarios activos de una
carrera, día y franja horaria. Útil para detectar superposiciones visualmente.

---

## 6. Docentes

Menú → **Docentes**.

- **+ Nuevo docente**: DNI, legajo (opcional), nombre, apellido, email y teléfono.
- **Editar**: actualizar datos personales.
- **Dar de baja**: marca al docente como inactivo. Sus modelos faciales y
  consentimientos quedan en el historial.
- **Reactivar**: vuelve a ponerlo en funciones y borra la fecha de baja.

### Las dos fechas

**La fecha de alta la pone el sistema solo**, el día que cargás al docente. No
aparece en el formulario y tampoco se edita: es el registro de cuándo ingresó al
sistema, no un campo más del legajo.

**La fecha de baja sí la elegís vos**, en el cuadro que aparece al dar de baja.
El campo se llama *Último día en funciones* y viene con la fecha de hoy puesta,
pero se puede correr hacia atrás.

> **Por qué la diferencia.** El alta ocurre mientras estás ahí cargando al
> docente: pedirte que además tipees la fecha solo agrega la posibilidad de
> equivocarte. La baja, en cambio, casi siempre se carga después del hecho —el
> docente dejó de prestar servicios el viernes y vos lo cargás el lunes—, así que
> forzar "hoy" falsearía el registro.

El sistema no acepta una fecha de baja futura ni anterior al alta del docente.

Si un docente fue dado de baja antes de que el sistema guardara esta fecha, su
ficha lo dice: *"Sin registrar"*. Es preferible a mostrar una fecha inventada.

En la ficha del docente (modo editar) hay dos tarjetas adicionales:

- **Consentimiento biométrico** — ver sección 7.
- **Modelo facial** — ver sección 8.

---

## 7. Consentimiento biométrico

**Cumplimiento Ley 25.326 + Resolución AAIP 255/2022.**

Antes de registrar el rostro del docente es **obligatorio** que tenga un
consentimiento vigente.

### Otorgar
1. Ficha del docente → tarjeta **"Consentimiento biométrico"** → **"Otorgar consentimiento"**.
2. La pantalla muestra el **texto legal completo** (7 cláusulas).
3. Tildá la casilla **"Confirmo que el docente leyó la versión X del texto y la firmó voluntariamente"**.
4. **"Registrar consentimiento"**.

> Lo que queda registrado: versión del texto, fecha y hora, tu usuario, tu IP
> y tu navegador (auditoría forense). Esto se hace en representación del
> docente que firmó el papel físicamente.

### Revocar
1. Ficha del docente → tarjeta **"Consentimiento biométrico"** (con badge **Vigente**) → **"Revocar consentimiento"**.
2. Cargá el motivo (opcional pero recomendado).
3. Tildá la casilla de confirmación.
4. **"Revocar consentimiento"**.

> La revocación es **inmediata e irreversible**. El modelo facial sigue
> existiendo en BD pero deja de usarse hasta que se otorgue un nuevo
> consentimiento.

---

## 8. Registro del rostro del docente

(Requiere consentimiento ACTIVO)

1. Ficha del docente → tarjeta **"Modelo facial"** → **"Registrar rostro"** (o **"Actualizar rostro"** si ya tenía uno).
2. **"Encender cámara"** → el navegador pide permiso.
3. **"Iniciar grabación"** → se graban 30 segundos del rostro del docente
   (configurable). Durante la grabación:
   - Indicador **REC** parpadeante en la cámara.
   - Recuadro amarillo en vivo sobre el rostro detectado (feedback).
4. Al finalizar, el sistema entrena el modelo LBPH con los frames válidos
   (descarta los borrosos o sin rostro), lo cifra con AES, y lo guarda.
5. Si éxito: badge **Registrado** en la ficha. Si error: leer el mensaje
   y volver a grabar.

> Las imágenes **no se persisten**. Solo se guarda el modelo entrenado
> (cifrado). Cumple RF-08, RNF-07/08.

### Re-registro (RF-09)
Hacer clic en **"Actualizar rostro"** repite el proceso. El modelo anterior
queda **dado de baja** automáticamente; el nuevo queda activo.

### Suprimir los datos biométricos (derecho ARCO)

Si el docente pide que se eliminen sus datos faciales —es su derecho por la Ley
25.326— usá **"Suprimir datos biométricos"** en su ficha.

> ⚠️ **Esta acción no se puede deshacer.** A diferencia del resto del sistema, que
> da de baja sin borrar, acá **se elimina de verdad**: se borran todos los modelos
> del docente, incluidos los históricos, y también la copia que el sistema tenía
> cargada en memoria. Una ficha marcada como "inactiva" seguiría conteniendo el
> dato biométrico, y ante un pedido de supresión eso no alcanza.

**Qué se conserva:** las asistencias ya registradas. Quedan intactas como registro
administrativo, pero sin ninguna referencia biométrica. Si el docente vuelve a dar
su consentimiento, hay que registrarle el rostro de nuevo desde cero.

---

## 9. Pase de asistencia automático

Menú → **Pase de asistencia**.

1. **"Encender cámara"** (toggle).
2. **"Iniciar pase"** (toggle): empieza el loop continuo.
3. El docente se para frente a la cámara. El sistema:
   - **Verde** + nombre + "Asistencia marcada: PRESENTE" → todo bien.
   - **Verde** + nombre + "Asistencia marcada: TARDE" → llegó después del `hora_inicio`.
   - **Azul** + "Ya estaba marcado" → ya había marca para esa clase.
   - **Amarillo** + "No hay clase ahora para X" → reconocido pero sin horario activo.
   - **Rojo** + "Rostro no reconocido" → la cara no matchea ningún modelo.
4. Tras cada marca exitosa, el sistema **pausa 5 segundos** antes de buscar
   otro rostro (evita ruido).
5. **"Detener pase"** + **"Apagar cámara"** al terminar la sesión.

### Cómo decide PRESENTE vs TARDE
- Antes del `hora_inicio` (pero dentro de la tolerancia) → **PRESENTE**.
- Después del `hora_inicio`, hasta el `hora_fin` → **TARDE**.
- Fuera de esa ventana → no hay clase, no se marca.

---

## 10. Listado de asistencias

Menú → **Asistencias**.

- **Filtros**: fecha (default hoy), estado, docente.
- Cada fila muestra hora exacta de marca, docente, materia, comisión, horario,
  estado y método (AUTOMATICO o MANUAL).
- Las filas en **gris claro** con badge AUSENTE son **calculadas**: horarios
  cuyo `hora_fin` ya pasó y no tienen marca. **No están persistidas en BD**
  hasta que las cargues manualmente.

### Acciones por fila
- **Justificar** → en filas AUSENTE persistidas (no calculadas).

---

## 11. Carga manual de asistencia

(Cuando falla el reconocimiento o hay que cargar una marca de oficio.)

1. Botón **"+ Cargar manual"** del header del listado.
2. Completar:
   - Docente.
   - Horario.
   - Fecha y hora exacta.
   - Estado (PRESENTE, TARDE o AUSENTE).
   - **Motivo** del catálogo (FALLA_CAMARA, FALLA_RECONOCIMIENTO, NO_REGISTRADO, OTRO).
   - Detalle adicional opcional.
3. **"Guardar asistencia"**.

> No se puede cargar manual si ya hay una marca para el mismo
> `(docente, horario, fecha)`. Para sobrescribir, dar de baja la anterior
> primero (no implementado en Sprint 5).

---

## 12. Justificar una ausencia

Solo aplica a marcas AUSENTE **persistidas**.

> **No hace falta cargarlas a mano.** El sistema revisa cada 30 minutos las clases
> que ya terminaron y, para las que no tienen marca, deja registrada la ausencia
> sola. Recién ahí aparece el botón de justificar.
>
> Si acabás de ver una fila AUSENTE en gris y todavía no te deja justificarla, es
> porque la clase terminó hace poco y el sistema aún no pasó a registrarla. Esperá
> unos minutos o cargala manualmente si necesitás resolverlo en el momento.

1. En el listado, fila AUSENTE → botón **"Justificar"**.
2. Cargar el **motivo** (texto libre, obligatorio).
3. Opcionalmente, pegar la **URL del documento** (certificado médico, etc.).
4. **"Justificar ausencia"**.

> En el listado y en el reporte, la columna **"Justif."** mostrará "Sí" en
> verde cuando la ausencia esté justificada.

---

## 13. Reportes y exportación CSV

Menú → **Reportes**.

1. Filtros: rango de fechas (obligatorio, default mes actual), docente,
   materia, estado, método.
2. **"Aplicar"** muestra los resultados en la tabla.
3. **"⬇ Descargar CSV"** baja el archivo con todas las columnas (incluyendo
   docente, materia, horario, hora exacta, motivo de carga manual,
   detalle, usuario que cargó, y motivo de justificación).

> El CSV usa **UTF-8 con BOM** y separador `;`. Se abre directo en Excel
> (Argentina) sin problemas de acentos ni columnas mezcladas.

---

## 14. Mi cuenta: verificar el correo y recuperar la contraseña

### Verificar tu correo

**Es obligatorio.** Mientras tu cuenta no tenga el correo confirmado, el sistema te
devuelve a esta pantalla cada vez que intentes entrar a cualquier otra. Lo único que
podés hacer es verificar o salir.

Menú → **Mi cuenta**.

1. Tocá **"Enviarme el código"**. El sistema manda un código de **seis dígitos** a
   la dirección de tu cuenta.
2. Escribilo en la pantalla y confirmá.

Listo: el sistema se desbloquea **al instante**, sin necesidad de volver a iniciar
sesión.

El código **vence a los 15 minutos** y sirve una sola vez. Si lo pedís de nuevo, el
anterior deja de funcionar. Después de cinco intentos fallidos el código se anula y
hay que pedir uno nuevo.

> **Por qué es obligatorio.** Un correo verificado es lo que te permite recuperar la
> contraseña sin depender de nadie. Si la dirección tiene un error de tipeo, nadie se
> entera hasta el día que la necesitás — y ese día ya es tarde.

### Recuperar la contraseña olvidada

No hace falta estar dentro del sistema.

1. En la pantalla de ingreso, tocá **"¿Olvidaste tu contraseña?"**.
2. Escribí tu usuario o tu correo.
3. Revisá tu casilla: llega un código de seis dígitos.
4. Escribí el código y tu contraseña nueva.

> La pantalla **siempre responde lo mismo**, exista o no la cuenta. Es a propósito:
> si respondiera distinto, cualquiera podría averiguar qué correos tienen cuenta en
> el sistema probando direcciones.

### Si el código no llega

- Fijate en la carpeta de correo no deseado.
- Verificá que la dirección de tu cuenta sea la correcta (pantalla **Mi cuenta**).
- Solo se permiten **cinco pedidos por hora** por cuenta. Si los agotaste, esperá.
- Si el servidor de correo de la institución está caído, ningún código va a llegar.
  Según lo que necesites:
  - **Olvidaste la contraseña**: pedile a la cuenta institucional que te la resetee
    a mano desde la pantalla de Usuarios. Ese camino sigue disponible.
  - **Tu cuenta quedó bloqueada sin verificar**: el reseteo de contraseña *no*
    desbloquea. Hay que avisarle a quien administra el servidor, que tiene un
    procedimiento de desbloqueo manual (Manual Técnico, sección 12).

---

## 15. Buenas prácticas

### Registro de docentes nuevos
1. Crear el docente en **Docentes**.
2. Cargar su **consentimiento biométrico** firmado.
3. Registrar su **rostro** con buena iluminación, mirando de frente.
4. Asignarle una o más **comisiones** (en el módulo Comisiones).
5. Probá el pase desde **Pase de asistencia** antes del primer día de clase.

### Antes de empezar las clases del día
- Verificá que el día y la hora estén bien en el reloj del servidor.
- Encendé la cámara desde **Pase de asistencia** con anticipación para que
  el navegador inicialice el video.

### Cuando algo falla
- Si la cámara no enciende → permiso del navegador, otro programa la está usando.
- Si nadie se reconoce → revisar luminosidad; calibrar el `umbral-confianza`
  (manual técnico).
- Si pasaron días sin clase y aparecen muchos AUSENTE → es esperable; podés
  cargarlos manualmente con motivo *OTRO + detalle*.
- Si al guardar aparece que un dato "ya existe" → hay otro registro con ese
  mismo valor. El mensaje dice cuál es y en qué ámbito no se puede repetir:
  algunos datos son únicos dentro de tu institución (DNI y legajo de un docente,
  usuario y correo de una cuenta, códigos de carrera y materia) y otros lo son
  en todo el sistema (nombre y CUIT de la institución).
- Si no se puede dar de baja a un docente → todavía es titular de alguna materia
  o está asignado a comisiones activas. El mensaje dice cuántas. Reasignalas
  primero y volvé a intentar.

### Backups
- La BD MariaDB debe respaldarse periódicamente. Ver **manual técnico**.

---

*Última actualización: Sprint 6.*
