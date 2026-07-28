# 05 — Desarrollo de un Caso de Uso

| | |
|---|---|
| **Proyecto** | Sistema de Asistencias Digital con Reconocimiento Facial |
| **Documento** | 05 — Casos de Uso del Sistema y Desarrollo de un Caso de Uso |
| **Materia** | Prácticas Profesionalizantes III — CENT35 |
| **Ubicación** | Provincia de Tierra del Fuego, Argentina |
| **Autor** | Francisco Quiroga |
| **Versión** | 1.0 |
| **Fecha** | Junio 2026 |

---

## 1. Introducción

Un **caso de uso** describe la interacción entre un actor y el sistema para
lograr un objetivo concreto. Este documento presenta primero el **panorama
de casos de uso** del sistema (qué puede hacer cada actor) y luego
**desarrolla un caso de uso completo** con la plantilla estándar, a modo de
ejemplo del nivel de detalle. El caso de uso principal del sistema —
*Registrar asistencia automática*— se desarrolla por separado en el
documento 06.

### Plantilla utilizada
Cada caso de uso desarrollado contiene: identificador, nombre, actor
principal, actores secundarios, descripción, precondiciones,
postcondiciones, flujo principal, flujos alternativos, excepciones y reglas
de negocio.

---

## 2. Actores

| Actor | Descripción |
|---|---|
| **Institución (Superadministrador)** | Cuenta raíz de la entidad educativa. Gestiona los administradores. |
| **Administrador** | Opera el sistema día a día: gestión académica, docentes, asistencias, reportes. |
| **Docente** | Sujeto pasivo. No opera la interfaz; se posiciona frente a la cámara. |
| **Sistema de Reconocimiento** | Componente automático (OpenCV/LBPH) que identifica al docente. |

---

## 3. Panorama de casos de uso por actor

| ID | Caso de uso | Actor principal | Requerimiento |
|---|---|---|---|
| CU-01 | Iniciar sesión | Administrador / Institución | RF-01 |
| CU-02 | Gestionar administradores | Institución | RF-06 |
| CU-03 | Gestionar carreras | Administrador | RF-11 |
| CU-04 | Gestionar materias | Administrador | RF-12 |
| CU-05 | Gestionar comisiones | Administrador | RF-13 |
| CU-06 | Gestionar horarios | Administrador | RF-14 |
| CU-07 | **Registrar docente** | Administrador | RF-07 |
| CU-08 | Otorgar consentimiento biométrico | Administrador | RF-10 |
| CU-09 | Registrar modelo facial | Administrador | RF-08, RF-09 |
| CU-10 | **Registrar asistencia automática** | Docente / Sistema | RF-15 a RF-21 |
| CU-11 | Cargar asistencia manual | Administrador | RF-22 a RF-24 |
| CU-12 | Justificar ausencia | Administrador | RF-25, RF-26 |
| CU-13 | Generar y exportar reportes | Administrador | RF-27 a RF-32 |

> El diagrama UML de casos de uso completo está disponible en el repositorio
> ([`docs/5-diagramas/diagramas.md`](../5-diagramas/diagramas.md)), que GitHub renderiza al abrirlo.

---

## 4. Caso de uso desarrollado: **CU-07 — Registrar docente**

| Campo | Detalle |
|---|---|
| **ID** | CU-07 |
| **Nombre** | Registrar docente |
| **Actor principal** | Administrador |
| **Actores secundarios** | — |
| **Requerimiento asociado** | RF-07 |
| **Descripción** | Permite al administrador dar de alta un nuevo docente en su institución, con sus datos personales y de contacto. |

### Precondiciones
1. El administrador está autenticado en el sistema.
2. El administrador tiene rol INSTITUCION o ADMIN.
3. Existe al menos una institución activa (la del administrador).

### Postcondiciones
- **Éxito**: queda registrado un nuevo docente (estado activo), asociado a la
  institución del administrador, disponible para vincularse a materias y
  comisiones.
- **Fracaso**: no se crea ningún registro; el sistema informa el motivo.

### Flujo principal (camino feliz)
1. El administrador accede al módulo **Docentes**.
2. El administrador selecciona **"Nuevo docente"**.
3. El sistema muestra el formulario de alta.
4. El administrador ingresa: DNI, legajo (opcional), nombre, apellido, email,
   teléfono y fecha de alta.
5. El administrador confirma el alta.
6. El sistema valida el formato de los datos.
7. El sistema verifica que el DNI no esté ya registrado en la institución.
8. El sistema registra el docente con estado activo, asociado a la
   institución del administrador.
9. El sistema muestra un mensaje de confirmación y redirige al listado de
   docentes, donde aparece el nuevo registro.

### Flujos alternativos
- **6a. Datos con formato inválido** (campos obligatorios vacíos, email mal
  formado, fecha futura): el sistema resalta los errores y vuelve al paso 4
  sin guardar.
- **7a. DNI ya registrado en la institución**: el sistema informa que ya
  existe un docente con ese DNI y vuelve al paso 4 sin guardar.

### Excepciones
- **E1. Sesión expirada**: si la sesión caducó, el sistema redirige al login
  y descarta la operación.
- **E2. Error de persistencia**: ante una falla técnica al guardar, el
  sistema informa el error y no deja datos parciales.

### Reglas de negocio
- **RN1**: el DNI debe ser único dentro de la institución (no a nivel global).
- **RN2**: el legajo, si se ingresa, también debe ser único en la institución.
- **RN3**: la baja de docentes es lógica (campo `activo`), nunca física.
- **RN4 (aislamiento multi-tenant)**: el docente queda asociado únicamente a
  la institución del administrador; ningún otro tenant puede verlo ni
  modificarlo.
- **RN5**: la fecha de alta no puede ser posterior a la fecha actual.
