# Guía de prueba de las correcciones

**Fecha**: 2026-07-30
**Alcance**: los 12 puntos relevados al cargar los primeros modelos faciales, resueltos en cinco bloques.

Este documento sirve para dos cosas: dejar constancia de qué se corrigió, y permitir
verificarlo punto por punto sin conocer el código.

---

## Antes de empezar

1. Arrancar **MariaDB** desde el panel de XAMPP.
2. Definir la clave de instalación y levantar la aplicación:

```bash
export INSTALACION_CLAVE="cent35-instalacion-2026"
```

```bash
./gradlew bootRun
```

3. Abrir `http://localhost:8080`.

> Al arrancar, Flyway aplica la migración **V008**, que agrega la fecha de baja de
> los docentes. Se aplica sola; no hay que hacer nada.

**Cuentas para las pruebas** (institución CENT35):

| Usuario | Contraseña | Rol |
|---|---|---|
| `superadmin.cent35` | `super123` | Institución |
| `admin.cent35` | `admin123` | Administrador |

Algunas pruebas necesitan la cuenta **Institución**, porque el módulo de usuarios
no está disponible para el rol Administrador.

---

## Resumen de lo corregido

| # | Punto relevado | Bloque | Estado |
|---|---|---|---|
| 1 | El botón "Detener" no frenaba el pase y obligaba a apagar la cámara | 1 | ✅ |
| 2 | Sacar "distancia: nro (menor = más parecido)" de la pantalla | 1 | ✅ |
| 3 | Esperar 3 segundos antes del siguiente escaneo | 1 | ✅ |
| 4 | La pantalla del pase no entraba completa | 2 | ✅ |
| 5 | No había forma de crear una institución ni su primer usuario | 3 | ✅ |
| 6 | Verificar la cuenta por PIN al correo, y no dejar operar hasta hacerlo | 3 | ✅ |
| 7 | Una institución no puede repetirse; un administrador sí puede compartir correo con un docente | 3 | ✅ |
| 8 | Fecha de alta automática; solo la de baja se elige | 4 | ✅ |
| 9 | Mensajes claros cuando un dato ya existe | 4 | ✅ |
| 10 | Toda la interfaz en español ("Username", "Ultimo login") | 5 | ✅ |
| 11 | Cambiar contraseña dentro de las acciones de edición | 5 | ✅ |
| 12 | Filtros en todos los listados, y menú con iconos y agrupación | 5 | ✅ |

---

# Cómo probar cada punto

---

## Bloque 1 — Pase de asistencia

### 1.1 El botón "Detener" ahora frena de verdad

**Antes**: al presionar "Detener" durante la pausa que sigue a una marca, el pase
volvía a arrancar. Quedaban dos ciclos corriendo a la vez y la única salida era
apagar la cámara.

**Cómo probarlo**

1. Menú → **Asistencias → Pase de asistencia**.
2. **Encender cámara** → **Iniciar pase**.
3. Ponerse frente a la cámara hasta que marque una asistencia.
4. **Justo en los 3 segundos siguientes a la marca**, presionar **Detener pase**.

**Qué tiene que pasar**: el pase se detiene y no vuelve a arrancar solo. El botón
queda en "Iniciar pase" y la cámara sigue encendida.

**El detalle**: el momento exacto importa. El error solo aparecía si se presionaba
*durante* la pausa posterior a una marca, no en cualquier momento.

---

### 1.2 La distancia ya no se muestra

**Cómo probarlo**: en la pantalla del pase, marcar una asistencia.

**Qué tiene que pasar**: aparece el nombre y el resultado, y **no** aparece
`distancia: 47.3 (menor = más parecido)`. Ese número sigue existiendo: se
registra en el log del servidor para poder calibrar el umbral, pero no en pantalla.

---

### 1.3 Tres segundos entre escaneos

**Cómo probarlo**: con el pase corriendo, marcar una asistencia y contar.

**Qué tiene que pasar**: pasan **3 segundos** hasta el siguiente intento (antes
eran 5). Es el tiempo para que la persona se corra y entre la siguiente.

---

## Bloque 2 — La pantalla del pase entra completa

**Cómo probarlo**

1. Abrir **Pase de asistencia** en una notebook.
2. Verificar que se ven **a la vez**: el video, el mensaje de estado y los botones,
   sin tener que bajar con la rueda del mouse.
3. Achicar la ventana en altura (o probar en una pantalla de 13").

**Qué tiene que pasar**: el video se achica para que los botones sigan a la vista.
Si la pantalla es muy baja, el subtítulo desaparece antes que los controles.

---

## Bloque 3 — Alta de institución y verificación obligatoria

### 3.1 Crear una institución nueva

**Cómo probarlo**

1. **Cerrar sesión**.
2. En la pantalla de ingreso, abajo: **"Registrar una institución nueva"**.
3. Completar con una **clave de instalación equivocada** y enviar.
   - **Qué tiene que pasar**: rechaza con *"La clave de instalación no es correcta."*
     y **no crea nada**.
4. Volver a completar, ahora con `cent35-instalacion-2026`:
   - Nombre: `Escuela de Prueba 9`
   - CUIT: (se puede dejar vacío)
   - Usuario, correo, contraseña (mínimo 8), nombre y apellido.
5. Enviar.

**Qué tiene que pasar**: vuelve a la pantalla de ingreso. Ya se puede entrar con
esa cuenta, **sin tener que verificar el correo**.

**Por qué esa cuenta no verifica**: quien la creó ya demostró tener la clave de
instalación, que es una prueba más fuerte que un código por correo. Si tuviera que
verificar y el servidor de correo estuviera caído, la institución nacería sin
acceso a su propia cuenta de gestión.

---

### 3.2 Una institución no puede repetirse

**Cómo probarlo**: repetir el alta con el **mismo nombre** que acabás de usar.

**Qué tiene que pasar**: *"Ya hay una institución registrada con ese nombre."*
El nombre y el CUIT son únicos en **todo el sistema**, no por institución.

---

### 3.3 El mismo correo puede repetirse entre instituciones

**Cómo probarlo**

1. Entrar como `superadmin.cent35`.
2. **Personal → Usuarios del sistema → + Nuevo usuario**.
3. Usar un correo que ya tenga un **docente** de esa misma institución
   (por ejemplo `jperez@cent35.edu.ar`).

**Qué tiene que pasar**: **lo acepta**. Un docente no es un usuario del sistema, así
que no compiten por el correo.

4. Ahora crear otro usuario con un correo que ya tenga **otro usuario de esta misma
   institución**.

**Qué tiene que pasar**: lo rechaza, aclarando que la misma persona **sí** puede
tener cuenta en otra institución con ese correo.

**Por qué**: en la provincia es normal trabajar en más de un establecimiento.
Forzar un correo distinto por institución obligaría a inventar direcciones falsas.

---

### 3.4 Una cuenta sin verificar no puede hacer nada

Esta es la corrección más importante del bloque.

**Cómo probarlo**

1. Entrar con una cuenta cuyo correo figure **Pendiente** en el listado de usuarios.
2. Intentar ir a cualquier pantalla: Docentes, Carreras, Reportes, Pase de asistencia.

**Qué tiene que pasar**: **todas** rebotan a **Mi cuenta**, con el aviso de que
falta verificar. Lo único que se puede hacer es verificar o salir.

3. En **Mi cuenta** → **Enviarme el código**.
4. Ingresar el código de 6 dígitos.

**Qué tiene que pasar**: el sistema se desbloquea **al instante**, sin necesidad de
cerrar sesión y volver a entrar.

> **Ese último punto es el que hay que mirar con atención.** Es donde este tipo de
> bloqueo suele fallar: la sesión se arma al iniciarla, así que si el sistema se
> apoyara solo en esa foto inicial, la persona verificaría correctamente y seguiría
> encerrada hasta cerrar sesión, sin ninguna explicación visible.

**Si no tenés un servidor de correo a mano**, el código igual queda registrado.
Para verlo:

```bash
/c/xampp/mysql/bin/mysql.exe -u asistencias -p asistenciautomatica -e "SELECT id, usuario_id, proposito, creado_en, expira_en FROM codigos_verificacion ORDER BY id DESC LIMIT 3;"
```

El código en sí **no se puede leer**: se guarda hasheado, igual que las contraseñas.
Para desbloquear sin correo está el procedimiento del **Manual Técnico, sección 12**.

---

## Bloque 4 — Las fechas del docente y los mensajes de choque

### 4.1 La fecha de alta ya no se pide

**Cómo probarlo**: **Personal → Docentes → + Nuevo docente**.

**Qué tiene que pasar**: el formulario pide DNI, legajo, apellido, nombre, correo y
teléfono. **No hay campo de fecha de alta.** Al crear, en el listado aparece con la
fecha de hoy en la columna **Alta**.

---

### 4.2 La fecha de baja sí se elige

**Cómo probarlo**

1. En el listado de docentes, **Dar de baja** a alguno que no sea titular de
   materias ni esté asignado a comisiones.
2. Aparece un cuadro con el campo **Último día en funciones**, con la fecha de hoy
   puesta.
3. Correrla algunos días hacia atrás y confirmar.

**Qué tiene que pasar**: el docente queda inactivo y en la columna **Baja** figura
**la fecha que elegiste**, no la de hoy.

**Por qué la diferencia con el alta**: el alta ocurre mientras estás ahí cargando al
docente, así que pedirte que la tipees solo agrega la chance de equivocarte. La baja
casi siempre se carga después del hecho —el docente dejó de prestar servicios el
viernes y vos lo cargás el lunes—, así que forzar "hoy" falsearía el registro.

**Probar también que rechaza lo imposible**:

| Qué elegir | Qué tiene que decir |
|---|---|
| Una fecha futura | *"La fecha de baja no puede ser futura."* |
| Una fecha anterior al alta del docente | *"La fecha de baja no puede ser anterior a la de alta (dd/mm/aaaa)."* |

> El propio selector ya limita el rango entre el alta y hoy, así que para llegar a
> esos mensajes hay que forzar la fecha a mano.

4. **Reactivar** al docente.

**Qué tiene que pasar**: la columna **Baja** vuelve a mostrar un guion. Un docente
activo que además figure desvinculado sería una contradicción.

---

### 4.3 Los mensajes cuando un dato ya existe

**Cómo probarlo**: crear un docente con un **DNI que ya esté cargado**.

**Qué tiene que pasar**: *"Ya existe un docente con DNI 'xxxxx' en esta institución."*

Lo mismo con el legajo, con el código de una carrera o de una materia, y con el
nombre de una institución.

**Qué NO tiene que pasar nunca**: una pantalla de error, ni un texto del estilo
`Duplicate entry '1-30111222' for key 'uq_docentes_inst_dni'`. Ese texto nombra la
tabla, el índice y el valor que se intentó guardar, que acá son datos personales.

**El detalle que se agregó**: cada mensaje aclara **en qué ámbito** el dato no se
puede repetir, que es la parte que confunde en un sistema con varias instituciones.

---

### 4.4 No se puede dar de baja a un docente con materias

**Cómo probarlo**: intentar dar de baja a un docente que sea titular de alguna
materia o esté asignado a comisiones activas.

**Qué tiene que pasar**: lo rechaza diciendo **cuántas** materias y cuántas
comisiones, y que hay que reasignarlas primero.

---

## Bloque 5 — Interfaz

### 5.1 Todo en español

**Cómo probarlo**: **Personal → Usuarios del sistema**.

**Qué tiene que pasar**: las columnas dicen **Usuario**, **Nombre completo**,
**Correo**, **Rol**, **Estado**, **Correo verificado** y **Último acceso**.

Ya no aparece "Username", "Email" ni "Ultimo login". El rol se lee **Institución** y
**Administrador**, no `INSTITUCION` ni `ADMIN`.

Revisar también los formularios de **nuevo usuario** y de **editar usuario**.

---

### 5.2 Cambiar contraseña, dentro de la ficha

**Cómo probarlo**

1. En el listado de usuarios, mirar la columna **Acciones**.

**Qué tiene que pasar**: hay un solo botón, **Editar**. El viejo "Cambiar pass" ya
no está.

2. Entrar a **Editar** de cualquier usuario y bajar.

**Qué tiene que pasar**: abajo hay una tarjeta **Acciones sobre la cuenta** con el
estado del correo, el último acceso y el botón **Cambiar contraseña**.

3. **Cancelar** desde ahí vuelve a la ficha, no al listado.

---

### 5.3 Filtros en los listados

**Cómo probarlo**: abrir **Docentes**. Arriba de la tabla hay una caja de búsqueda y
un selector de estado.

| Qué escribir | Qué tiene que pasar |
|---|---|
| Parte de un apellido | La tabla se recorta **mientras escribís**, sin recargar |
| `garcia` (sin tilde) | Igual encuentra a **García** |
| Un DNI o un legajo | También filtra: mira todas las columnas |
| Algo que no existe | *"Ningún docente coincide con la búsqueda."* |

A la derecha, el contador pasa de "3 registros" a "1 de 3".

El selector de estado permite ver **solo activos** o **solo inactivos**.

**Repetir en**: Usuarios, Carreras, Materias, Comisiones y Horarios. Son los seis
listados de catálogo.

> **Asistencias y Reportes funcionan distinto a propósito**: tienen un botón
> *Aplicar* porque filtran por fecha contra la base. Esas tablas crecen sin techo con
> el tiempo, así que no se pueden traer enteras para filtrarlas en pantalla.

---

### 5.4 El menú agrupado

**Cómo probarlo**: mirar la barra de arriba.

**Qué tiene que pasar**: en lugar de doce enlaces sueltos hay **Inicio** y tres
grupos con su icono:

| Grupo | Contenido |
|---|---|
| **Académico** | Carreras, Materias, Comisiones, Horarios, Grilla semanal |
| **Asistencias** | Pase de asistencia, Listado del día, Reportes |
| **Personal** | Docentes, Usuarios del sistema, Mi institución |

Cosas para verificar:

1. Clic en un grupo → se abre el desplegable.
2. Clic en **otro** grupo → el primero se cierra solo.
3. Clic **fuera** del menú → se cierra.
4. Tecla **Escape** → se cierra.
5. Estando en Docentes, el grupo **Personal** queda subrayado aunque esté cerrado.
6. **Achicar la ventana** hasta que aparezca el botón de las tres rayas: los grupos
   se muestran **abiertos**, como secciones con título. Ahí hay altura de sobra y un
   segundo clic solo agregaría un paso.

Con el rol **Administrador**, dentro de **Personal** aparece solo *Docentes*:
*Usuarios del sistema* y *Mi institución* son de la cuenta Institución.

---

## Qué mirar si algo no anda

| Síntoma | Dónde mirar |
|---|---|
| No arranca la aplicación | ¿MariaDB está levantado en XAMPP? |
| "El alta de instituciones está deshabilitada" | Falta `INSTALACION_CLAVE`. Se lee al arrancar: hay que definirla y reiniciar |
| El código de verificación no llega | Hace falta un SMTP. Manual Técnico, sección 3.7 |
| Una cuenta quedó encerrada sin poder verificar | Desbloqueo manual: Manual Técnico, sección 12 |
| El filtro no filtra | Recargar con Ctrl+F5: puede haber quedado el JavaScript viejo en caché |

---

## Referencias

- [ADR-0010](../4-arquitectura/adr/0010-alta-de-institucion-y-bloqueo-por-verificacion.md) — alta de institución y bloqueo por verificación.
- [ADR-0011](../4-arquitectura/adr/0011-errores-de-integridad-legibles.md) — traducción de los choques contra la base.
- [Manual del Administrador](../6-manuales/manual-administrador.md) — uso diario.
- [Manual Técnico](../6-manuales/manual-tecnico.md) — instalación, migraciones y desbloqueo de emergencia.
