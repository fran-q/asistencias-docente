# Marco legal

El sistema trata **datos biométricos**, que la Ley 25.326 de Protección de los Datos Personales clasifica como datos sensibles y la Resolución AAIP 255/2022 somete a un régimen reforzado: exigen consentimiento expreso, informado y demostrable, y obligan a proteger el dato con medidas de seguridad razonables.

Esta carpeta reúne los textos legales que la aplicación usa y cómo se cumple ese marco.

## Contenido

| Documento | Qué es |
|---|---|
| [consentimiento-v1.md](./consentimiento-v1.md) | Texto que el docente acepta antes de que se registre su rostro. Copia impresa del que muestra la aplicación. |

## Cómo cumple el sistema, en concreto

| Exigencia | Cómo se resuelve | Dónde verificarlo |
|---|---|---|
| Consentimiento expreso previo | No se puede registrar el modelo facial sin un consentimiento vigente | `ModeloFacialService.registrar` |
| Consentimiento **demostrable** | Cada aceptación guarda fecha, método, versión del texto, y la IP y el navegador del administrador que la cargó | Tabla `consentimientos_biometricos`, migración `V005` |
| No conservar más de lo necesario | **No se almacenan fotos.** Solo el modelo entrenado, que no permite reconstruir la imagen | ADR-0007 |
| Medidas de seguridad razonables | El modelo se guarda cifrado con AES-GCM y clave derivada por PBKDF2 | `CifradoBiometricoService` |
| Derecho de cancelación (ARCO) | La supresión **borra físicamente** todos los modelos del docente, incluidos los históricos, y los saca de la memoria del proceso | `ModeloFacialService.suprimirDatosBiometricos` |

La supresión física es la única excepción a la baja lógica que rige en el resto del sistema. El motivo está documentado: una fila marcada como inactiva **seguiría conteniendo el dato biométrico**, y ante un pedido de supresión el dato tiene que desaparecer de verdad. Las asistencias históricas se conservan porque la clave foránea es `ON DELETE SET NULL`: quedan los registros administrativos, sin la referencia biométrica.

## Pendiente

Dos documentos que el sistema todavía no necesita —no hay portal del docente ni tratamiento fuera del ámbito institucional— pero que corresponderían en un despliegue real:

- **Política de privacidad** general del tratamiento de datos personales.
- **Procedimiento ARCO** escrito, para que el docente sepa cómo ejercer sus derechos. Hoy el mecanismo técnico existe y se ejecuta a pedido, a través del administrador.

## Documentos relacionados

- [ADR-0005: Diseño del consentimiento biométrico](../4-arquitectura/adr/0005-consentimiento-biometrico.md)
- [ADR-0007: Reconocimiento facial](../4-arquitectura/adr/0007-reconocimiento-facial-lbph.md)
- [ADR-0009: Verificación de correo](../4-arquitectura/adr/0009-verificacion-correo-y-recuperacion.md) — incluye por qué se descartaron los servicios externos de validación.
