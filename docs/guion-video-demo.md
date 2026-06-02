# Guión sugerido para el video demo

Duración estimada: **8 a 10 minutos**.

Esta es una propuesta. Adaptala a tu estilo y al tiempo que pida tu tutor.

## Antes de grabar

1. **Datos de prueba listos**:
   - Una institución con un par de carreras y materias.
   - Al menos 2 docentes activos con su consentimiento y rostro registrado.
   - Al menos 1 comisión con horario que **esté corriendo cuando grabes**
     (clave para que el pase de asistencia se vea marcando PRESENTE).
2. **Limpiar la BD si hace falta**: borrar marcas anteriores del docente
   demo para que no aparezca "ya estaba marcado" desde el primer frame.
3. **Pantalla**: maximizada, sin notificaciones (modo no molestar), navegador
   limpio sin pestañas extra.
4. **Cámara y micrófono** OK.
5. Tener abierto en una pestaña aparte **phpMyAdmin** para mostrar la BD.

---

## Estructura

### 0. Intro (30 s)

> Hola, soy Francisco Quiroga. Este es el Sistema de Asistencias Digital
> con Reconocimiento Facial — proyecto de Prácticas Profesionalizantes III
> del CENT35. Permite que las instituciones educativas registren la
> asistencia de su personal docente automáticamente, usando la cámara
> del navegador.

### 1. Login y panel principal (30 s)

- Mostrar `/login`.
- Ingresar con un usuario admin.
- Recorrer el navbar mencionando los módulos.

### 2. Estructura académica (1 min)

- Click en **Carreras**: mostrar 1 o 2 ya cargadas.
- Click en **Materias**: mostrar el titular asignado.
- Click en **Comisiones**: mostrar el docente asignado.
- Click en **Horarios**: enfocar el horario con tolerancia configurada.
- Click en **Grilla**: mostrar la grilla semanal de una carrera.

### 3. Docentes y consentimiento (1.5 min)

- Click en **Docentes**: mostrar el listado con badges de consentimiento
  (Vigente / Sin firmar) y la nueva columna de modelo facial (Registrado).
- Click en un docente → mostrar la card de **Consentimiento biométrico**
  con sus datos (fecha, método, IP, usuario que lo cargó).
- Hablar 30 s sobre el marco legal (Ley 25.326, AAIP 255/2022) y cómo
  el sistema **no guarda fotos, solo el modelo entrenado y cifrado**.

### 4. Registro facial (rápido) (1 min)

- En la ficha del docente, click en **Actualizar rostro**.
- Mostrar la pantalla: cámara, recuadro amarillo sobre el rostro,
  contador de 30 segundos.
- **No tenés que esperar los 30 s reales en el video**. Cortá la grabación
  o usá un timelapse 4x.
- Volver a la ficha y mostrar el badge **Registrado**.

### 5. **Pase de asistencia (la estrella)** (2 min)

- Navbar → **Pase de asistencia**.
- Encender cámara → Iniciar pase.
- Mirando a la cámara: mostrar el recuadro **verde** + el nombre del
  docente + "Asistencia marcada: PRESENTE en Comisión X — Materia Y".
- Esperar la pausa de 5 s y mostrar el cambio a recuadro **azul** con
  "Ya estaba marcado".
- **Tapar la cara con la mano** → recuadro desaparece, mensaje cambia.
- **Mostrar el caso de otro docente**: pedile a alguien que no esté
  registrado que se pare frente a la cámara → recuadro rojo "Rostro
  no reconocido".

### 6. Listado de asistencias (1 min)

- Navbar → **Asistencias**.
- Mostrar la marca PRESENTE recién creada arriba de todo.
- Cambiar la fecha al día anterior si tenés marcas — mostrar AUSENTES
  calculadas en gris.
- Hablar 30 s sobre cómo la AUSENTE **se calcula al listar**: no hay
  un cron job, el sistema detecta los horarios sin marca cuya
  `hora_fin` ya pasó.

### 7. Carga manual + justificación (1 min)

- Botón **+ Cargar manual** en el header del listado.
- Llenar el form para una clase pasada: docente, horario, fecha, estado
  AUSENTE, motivo "FALLA_CAMARA", detalle "se cortó la luz a las 18:30".
- Guardar.
- Sobre esa fila → **Justificar**. Cargar motivo "Certificado médico
  presentado" + URL ficticia. Guardar.
- Volver al listado y mostrar la columna **Método = MANUAL** y la
  columna **Justif. = Sí**.

### 8. Reportes y exportación (1 min)

- Navbar → **Reportes**.
- Rango: mes actual.
- Click en **Aplicar** → mostrar la tabla.
- Click en **Descargar CSV**.
- Abrir el CSV en Excel/LibreOffice y mostrar las columnas: docente,
  materia, comisión, horario, hora exacta, estado, método, confianza,
  motivo manual, detalle manual, usuario que cargó, justificación.

### 9. Backend (15 s para alumnos técnicos)

- Mostrar phpMyAdmin → `asistencias` con la fila recién marcada.
- Mostrar el campo `confianza` (0.0000-1.0000).
- Mostrar `modelos_faciales.embedding_cifrado` como BLOB binario.
- Mencionar: "Esto es el modelo LBPH entrenado, comprimido con gzip y
  cifrado con AES-256-GCM. No es reconstruible a la imagen original".

### 10. Cierre (30 s)

> El sistema cubre el flujo completo de gestión de asistencias docentes,
> con reconocimiento facial real, idempotencia, multi-tenancy, y
> cumplimiento de la Ley 25.326. Todo el código y la documentación están
> versionados en GitHub. Gracias.

---

## Tips de grabación

- **OBS Studio** (gratis) para grabar la pantalla con la webcam superpuesta.
- Resolución: 1920×1080 a 30 fps.
- Audio: usar micrófono externo o headset, no el del notebook.
- **Hacé un ensayo completo sin grabar** antes de la toma definitiva.
- Si te equivocás, no cortes el video: **pausa larga + repetir la frase**.
  Después editás eso afuera.
- Edita en algo simple: **OpenShot**, **Shotcut** o **DaVinci Resolve**.
