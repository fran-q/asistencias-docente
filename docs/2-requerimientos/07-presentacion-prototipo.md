# 07 — Presentación del Prototipo

| | |
|---|---|
| **Proyecto** | Sistema de Asistencias Digital con Reconocimiento Facial |
| **Documento** | 07 — Presentación del Prototipo |
| **Materia** | Prácticas Profesionalizantes III — CENT35 |
| **Ubicación** | Provincia de Tierra del Fuego, Argentina |
| **Autor** | Francisco Quiroga |
| **Versión** | 1.0 |
| **Fecha** | Junio 2026 |

> **Nota para la edición final:** este documento tiene marcadores
> `📷 [Captura: …]` que indican dónde insertar las imágenes al exportar a
> Word/PDF. Las capturas se toman de la aplicación funcional en
> `http://localhost:8080`.

---

## 1. Introducción

El prototipo del sistema se presenta en dos formas complementarias:

1. **Prototipo funcional** — la aplicación web real, desarrollada y operativa,
   que implementa los flujos descritos en los documentos anteriores. Es un
   prototipo de **alta fidelidad y funcional** (no un mockup estático).
2. **Prototipo de diseño (UI)** — maquetas de alta fidelidad de la interfaz,
   generadas como apoyo visual y exportables a Figma, que muestran la
   dirección de diseño moderna pensada para uso administrativo diario.

---

## 2. Prototipo funcional (aplicación real)

### 2.1. Tecnologías de la interfaz
- Renderizado server-side con **Thymeleaf**.
- Estilo propio (CSS) con **modo oscuro**, diseño minimalista orientado a uso
  frecuente (RNF-21).
- Componentes JavaScript propios: notificaciones tipo *toast*, modales de
  confirmación, captura por webcam (`getUserMedia`), navbar adaptable.

### 2.2. Pantallas principales

#### Inicio de sesión
Autenticación por usuario y contraseña; sin recordar contraseña ni
copiar/pegar en el campo, por seguridad.

> 📷 [Captura: pantalla de login]

#### Panel de inicio
Pantalla principal tras autenticarse, con acceso a todos los módulos desde la
navegación.

> 📷 [Captura: panel de inicio]

#### Gestión académica (carreras, materias, comisiones, horarios)
CRUD completo con borrado lógico. Incluye la **grilla semanal** que muestra
los horarios de una carrera en formato calendario.

> 📷 [Captura: listado de materias]
> 📷 [Captura: grilla semanal]

#### Gestión de docentes
Listado con estado de consentimiento y de modelo facial. La ficha del docente
integra las tarjetas de **consentimiento biométrico** y **modelo facial**.

> 📷 [Captura: listado de docentes]
> 📷 [Captura: ficha del docente con consentimiento y modelo facial]

#### Registro del modelo facial
Captura por webcam con recuadro de detección en vivo; las imágenes no se
guardan, solo el modelo entrenado y cifrado.

> 📷 [Captura: pantalla de registro de rostro con la cámara]

#### Pase de asistencia (flujo principal)
Reconocimiento en vivo: recuadro verde con el nombre del docente y la materia
al marcar asistencia; estados visuales para los casos de no reconocido, sin
clase o ya marcado.

> 📷 [Captura: pase de asistencia con un rostro reconocido (recuadro verde)]

#### Listado de asistencias
Marcas del día con filtros; las ausencias se muestran calculadas.

> 📷 [Captura: listado de asistencias]

#### Carga manual y justificación
Respaldo ante fallas del reconocimiento, con motivo del catálogo; y
justificación de ausencias.

> 📷 [Captura: formulario de carga manual]

#### Reportes
Filtros por rango de fechas, docente, materia, estado y método; exportación a
CSV.

> 📷 [Captura: pantalla de reportes]

---

## 3. Prototipo de diseño (UI / Figma)

Como apoyo visual y para mostrar la evolución de la interfaz, se generaron
maquetas de alta fidelidad con un estilo moderno (dashboard administrativo,
modo oscuro, navegación lateral) pensado para administradores que usan el
sistema a diario.

> 📷 [Captura: dashboard del prototipo de diseño]
>
> 🔗 [Enlace al prototipo en Figma: _completar con el link_]

---

## 4. Flujo demostrado en el video

El video de demostración recorre el flujo completo del sistema:
1. Inicio de sesión.
2. Recorrido por la gestión académica y de docentes.
3. Otorgamiento de consentimiento y registro del modelo facial.
4. **Pase de asistencia automático** (flujo principal).
5. Listado de asistencias, carga manual y justificación.
6. Generación y exportación de reportes.

> 🔗 [Enlace al video de demostración: _completar con el link_]
>
> El guion detallado del video está disponible en el repositorio
> (`asistencias/docs/guion-video-demo.md`).

---

## 5. Cómo probar el prototipo funcional

1. Tener MariaDB en ejecución (XAMPP) y la base `asistenciautomatica` creada.
2. Ejecutar la aplicación: `./gradlew bootRun`.
3. Abrir `http://localhost:8080` en el navegador.
4. Iniciar sesión con un usuario administrador.
5. Para el pase de asistencia, permitir el acceso a la cámara cuando el
   navegador lo solicite.

> El detalle de instalación y configuración está en el **Manual Técnico**
> (`asistencias/docs/manuales/manual-tecnico.md`).

---

## 6. Conclusión

El prototipo entregado es **funcional y operativo**: cubre el flujo completo
de gestión y registro de asistencia docente mediante reconocimiento facial,
con cumplimiento de la normativa de datos biométricos. El estado de cada
requerimiento se detalla en el documento **02 — Definición del Alcance**.
