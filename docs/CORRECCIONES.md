# Correcciones y mejoras solicitadas

Bitácora de correcciones puntuales que el cliente pide durante la revisión del proyecto. Cada entrada queda con su estado, fecha de pedido y commit/ADR que la resuelve.

Convención de estados:
- 🟢 **Hecho**: implementado y verificado.
- 🟡 **En curso**: en pleno trabajo.
- 🔴 **Pendiente**: registrado, todavía no atacado.
- 🔵 **A discutir**: requiere clarificación antes de avanzar.

---

## Lote 2026-04-30 — Sprint 1, post Fase F

### C-001 · Acentuar "Contraseña" en formularios de login y password 🟢
**Pedido**: cambiar todas las apariciones de `Contrasena` por `Contraseña` (con ñ) en los textos visibles al usuario.
**Resuelto en**: commit `feat(ux): correcciones de UX login y tablas`.
**Notas**: Spring Boot maneja UTF-8 por default; los archivos `.html` y los mensajes de Bean Validation (`@Size`, `@NotBlank`) se rescribieron con ñ.

### C-002 · Mostrar/ocultar contraseña con ojo 🟢
**Pedido**: en todo input de tipo `password`, agregar un botón al costado con un ícono de ojo que toggle la visibilidad del texto.
**Resuelto en**: dos commits: primero con emojis (👁 / 🙈), después corregido a SVG inline (estilo Feather: ojo abierto / ojo tachado) tras feedback del cliente — el emoji 🙈 mostraba un mono, queriase un ojo cerrado.
**Detalle**: `password-ui.js` define los SVG como constantes y los inyecta como `innerHTML` del botón en init y en cada toggle. Sin emoji, vectorial, escala bien.

### C-003 · Bloquear copiar / pegar / cortar en password 🟢
**Pedido**: por seguridad, los inputs de password no deben permitir copiar, pegar ni cortar texto.
**Resuelto en**: mismo commit, mismo script. Listeners sobre `copy`, `paste` y `cut` con `preventDefault()`.
**Caveat**: bloquear `paste` rompe la integración con password managers (1Password, Bitwarden, etc.). Si en uso real los administradores se quejan, hay que revisarlo. Por ahora se mantiene como pidió el cliente.

### C-004 · Scroll horizontal con shift + rueda en tablas anchas 🟢
**Pedido**: cuando la pantalla es chica, la tabla de Usuarios se corta a la derecha; el cliente quiere poder scrollear horizontal con `Shift + rueda del ratón`.
**Resuelto en**: mismo commit. La tabla queda envuelta en `.table-wrap` con `overflow-x:auto`. Un script chico convierte `Shift + wheel` en scroll horizontal sobre ese contenedor.

### C-005 · Renombrar rol `SUPERADMIN_INSTITUCION` → `INSTITUCION` 🟢
**Pedido**: rebatizar el rol para que sea más estandar y limpio.
**Resuelto en**: commit `refactor: renombrar rol SUPERADMIN_INSTITUCION a INSTITUCION`.
**Detalle**:
- Migración Flyway `V003__rename_rol_superadmin_to_institucion.sql`: `UPDATE roles SET codigo='INSTITUCION'`.
- Enum `RolCodigo`: renombrado el value.
- Anotaciones `@PreAuthorize("hasRole('SUPERADMIN_INSTITUCION')")` → `hasRole('INSTITUCION')` y idem `sec:authorize`.
- Documentación (`credenciales_proyecto.txt`, ADRs) actualizada.
- **IMPORTANTE**: invalida la sesión actual. Hay que cerrar sesión y volver a loguearse después de aplicar la migración.

### C-006 · INSTITUCION en pantalla aparte de "Mi Institución" 🟢
**Pedido textual**: *"esos tipos de usuarios solo será accesible en otra pantalla aparte de los datos de la institución"*.

**Resolución**: la separación ya estaba implementada antes del pedido y se mantiene así:
- `/mi-institucion` administra **solo datos** de la institución (nombre, CUIT, contacto). No tiene listado ni edición de usuarios.
- `/usuarios` administra **solo usuarios** (incluyendo el INSTITUCION). No tiene datos institucionales.

Ambas son pantallas distintas, el usuario INSTITUCION es accesible "aparte de los datos de la institución" tal como se pidió. El badge visual de la tabla (INSTITUCION en naranja, ADMIN en gris) hace evidente la diferencia de roles.

**Si en uso real surge necesidad de mayor split** (`/usuarios` para solo ADMIN + nueva pantalla `/cuenta-institucion` dedicada), abrir un ítem nuevo (C-008+) en este mismo doc.

### C-007 · Notificaciones tipo Toast en lugar de alerts inline 🟢
**Pedido**: las notificaciones tipo "creado correctamente / error / etc" hoy aparecen como un banner grande arriba de la pantalla. Reemplazar por toasts en una esquina (verde / amarillo / rojo según severidad) que aparezcan, se desvanezcan, y no rompan el layout.

**Resuelto en**: commit `feat(ux): toasts para notificaciones`.

**Detalle**:
- `static/js/toast.js`: API `Toast.show(mensaje, tipo, duracion)` con tipos `success` (verde), `warning` (amarillo), `error` (rojo).
- CSS en `main.css`: `#toast-container` posicionado bottom-right, animaciones de entrada/salida.
- `templates/layout/base.html`: hidden `<div id="flash-data">` con los flash attributes; al cargar la pagina, JS los lee y los muestra como toasts.
- `templates/auth/login.html`: idem para `?error` (rojo) y `?logout` (verde).
- Errores de validacion in-form (Bean Validation) se siguen mostrando inline porque relacionan a campos especificos. Solo los flash attributes (post-redirect) se convierten en toasts.

---

---

## Lote 2026-05-05 — Sprint 2, durante Fase B

### C-008 · Doble ícono de ojo en password (Edge/IE) 🟢
**Síntoma**: al tipear algo en el input de contraseña y refrescar la página (autofill o restauración de form), aparecía un ojito gris pequeño *al lado* del ojo blanco SVG nuestro.
**Causa**: Microsoft Edge agrega automáticamente un botón nativo `::-ms-reveal` para "revelar contraseña" cuando el campo `type=password` tiene valor. Nuestro botón custom convivía con ese, dando dos ojos.
**Resuelto en**: commit `fix(ux): ocultar botones nativos de password reveal en Edge`.
**Detalle**: agregadas reglas CSS para ocultar:
```css
input[type="password"]::-ms-reveal,
input[type="password"]::-ms-clear { display: none; }
input[type="password"]::-webkit-credentials-auto-fill-button,
input[type="password"]::-webkit-strong-password-auto-fill-button { display: none !important; }
```
La primera silencia el reveal de Edge; la segunda silencia los íconos de autofill/strong-password de Safari/Chrome (cuando aplica). En Brave/Chrome/Firefox no había duplicado, pero las reglas no molestan.

---

## Cómo agregar nuevos pedidos

Al final de cada lote (sprint, sub-fase o sesión de revisión), abrir un nuevo bloque:

```markdown
## Lote YYYY-MM-DD — descripción corta

### C-NNN · Título 🔴
**Pedido**: ...
**Resuelto en**: -
**Notas**: -
```

Numeración correlativa global (no se reinicia entre lotes). Estado se actualiza al cerrar el ítem.
