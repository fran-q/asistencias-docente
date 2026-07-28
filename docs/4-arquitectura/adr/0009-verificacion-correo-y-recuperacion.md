# ADR-0009: Verificación de correo y recuperación de contraseña por código de un solo uso

**Estado**: Aceptada
**Fecha**: 2026-07-26
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Hasta este punto el sistema tenía dos huecos operativos conectados entre sí.

El primero: **no existía recuperación de contraseña**. Si un administrador olvidaba la suya, la única salida era que el superadmin se la reseteara a mano desde la pantalla de usuarios. Y si quien la olvidaba era el superadmin, no había salida: la institución quedaba sin acceso a su propia cuenta de gestión.

El segundo: **nada garantizaba que el correo cargado en una cuenta fuera real**. Los formularios validan el formato con `@Email` de Jakarta, pero eso solo comprueba la sintaxis. Una dirección con un error de tipeo (`admin@gmial.com`) o directamente inventada pasaba sin problema. Eso vaciaba de sentido cualquier recuperación basada en el correo: no serviría de vía de acceso, o peor, el código llegaría a un buzón ajeno.

Conviene separar tres cosas que suelen llamarse igual:

| Nivel | Qué comprueba | Estado previo |
|---|---|---|
| Sintaxis | Que tenga forma de dirección | Ya cubierto con `@Email` |
| Entregabilidad | Que el dominio pueda recibir correo | No cubierto |
| Control del buzón | Que la persona **tenga acceso** a esa casilla | No cubierto |

Solo el tercero aporta seguridad real, y es el único que habilita la recuperación autogestionada.

Es importante dejar constancia de que **esto no cubre ningún requerimiento pendiente**: no hay RF ni RNF que pida verificación de correo o recuperación de contraseña. Es alcance incorporado por calidad, para cerrar un riesgo operativo que el relevamiento original no había contemplado.

## Decisiones

### 1. Código de un solo uso (OTP de 6 dígitos), no enlace de verificación

El mecanismo habitual es enviar un enlace con un token y que la persona lo abra. **Acá no sirve**: la aplicación se despliega en `localhost`, así que un enlace solo funciona si el correo se abre en la misma máquina donde corre el servidor. Si el mensaje llega a un teléfono, el enlace muere.

Con un código de seis dígitos la persona lo tipea en la pantalla, y el flujo deja de depender de que la URL sea alcanzable desde donde se leyó el mensaje. Es la misma razón por la que los bancos usan códigos y no enlaces.

### 2. Una sola tabla para los dos propósitos

`codigos_verificacion` sirve tanto a la verificación de correo como a la recuperación de contraseña, distinguidas por la columna `proposito`.

Los dos flujos comparten exactamente el mismo ciclo de vida (emitir, enviar, validar, invalidar) y las mismas defensas. Duplicar la tabla habría duplicado también la lógica de expiración e intentos, que es justamente donde se cometen los errores de seguridad: alcanza con que una de las dos copias se quede sin el tope de intentos para abrir el agujero.

### 3. El código se guarda hasheado, nunca en claro

Se aplica el mismo criterio que a las contraseñas: en la base queda un hash BCrypt. Quien lea la tabla —por un volcado, un backup mal guardado o una inyección— no puede usar un código pendiente.

### 4. Defensas sobre el código

| Defensa | Por qué |
|---|---|
| Vence a los 15 minutos | Un código filtrado deja de servir enseguida |
| Se consume en el primer uso | Reutilizarlo no revalida nada |
| Máximo 5 intentos fallidos | Seis dígitos son un millón de combinaciones; **sin tope se prueban por fuerza bruta** |
| Máximo 5 pedidos por hora | Evita que el sistema sirva de generador de correo no deseado |
| Emitir uno nuevo invalida el anterior | No quedan varios códigos válidos a la vez |
| Se genera con `SecureRandom` | Un `Random` común es predecible: conociendo la semilla se anticipa el próximo |

### 5. La recuperación no revela si una cuenta existe

`/recuperar` es, junto con el login, la única zona accesible sin sesión. Responde **exactamente lo mismo** exista o no la cuenta: mismo estado HTTP, misma redirección, mismo texto.

Si contestara distinto, alcanzaría con probar direcciones para averiguar quién tiene cuenta en el sistema — un ataque de enumeración. La misma lógica se aplica cuando falla el envío del correo o cuando se alcanzó el límite de reenvíos: se registra en el log y se responde igual.

**Tampoco se muestra el correo enmascarado.** Es habitual que estas pantallas digan "lo enviamos a `a****@dominio.com`", y la primera versión de esta implementación lo hacía. Se quitó: aunque la dirección esté tapada, mostrarla **solo cuando la cuenta existe** ya delata su existencia, y de paso confirma la inicial y el dominio. La comodidad de recordarle a la persona a qué casilla mirar no compensa perder la propiedad que esta decisión buscaba.

Se detectó probando el flujo a mano contra un servidor de correo local: los tests comparaban el estado HTTP y la redirección, que eran idénticos en ambos casos, pero la diferencia aparecía recién en el HTML de la pantalla siguiente. El test se reforzó para seguir el flujo hasta la página renderizada y compararla entera, normalizando el token CSRF, que por definición cambia en cada sesión.

El identificador de la persona viaja **en la sesión y no en la URL**. Si viajara por parámetro, cualquiera podría pedir el cambio de contraseña de otra cuenta simplemente escribiendo otro id.

### 6. Los docentes quedan fuera del alcance

Se verifica el correo de las cuentas con rol `INSTITUCION` y `ADMIN`. **El de los docentes no.**

El motivo es de dominio, no técnico: el docente **no es usuario del sistema**. No tiene cuenta ni inicia sesión; es una entidad administrada por el personal administrativo, y su correo es un dato de contacto opcional. Pedirle que confirme una casilla sería exigirle una acción dentro de una aplicación que nunca abre.

Si en el futuro se implementa el login de docente —previsto en el comentario de la migración `V005`— el mecanismo ya está construido y solo habría que aplicarlo a esas cuentas.

Para la calidad del dato de contacto del docente, la alternativa razonable sería una consulta MX por DNS al guardar, que detecta dominios inexistentes sin pedirle nada a nadie. Queda como mejora posible, no implementada.

### 7. El servidor de correo es configuración, no código

El envío usa `JavaMailSender` sobre SMTP estándar. La aplicación no sabe a qué servidor le escribe: host, puerto, credenciales y TLS salen de variables de entorno.

En desarrollo apunta a un SMTP local de captura (Mailpit, MailHog o similar en el puerto 1025), que muestra los mensajes en una interfaz web sin mandarlos a internet. Para la demostración esto es preferible a un envío real: no depende de conexión, ni de una cuenta de terceros, ni de que el mensaje no caiga en la carpeta de correo no deseado.

## Alternativas descartadas

### Servicios externos de validación de correo (ZeroBounce, Hunter, Abstract)

Comprueban con buena precisión si una casilla existe. Se descartan por **marco legal**: implicaría enviar las direcciones de terceros a un servicio externo, casi siempre fuera del país, lo que constituye una transferencia internacional de datos personales bajo la Ley 25.326. Además son de pago y cerrados, lo que choca con la restricción de usar solo herramientas de código abierto.

### Sondeo SMTP (`VRFY` / `RCPT TO` sin enviar mensaje)

Técnicamente permitiría preguntarle al servidor destino si la casilla existe. Se descarta porque **no funciona de forma confiable**: la mayoría de los servidores desactivaron `VRFY` justamente para frenar la recolección de direcciones, los dominios con captura genérica responden que sí a cualquier casilla, y el patrón de conexiones puede hacer que el servidor quede marcado como emisor de correo no deseado.

### Confirmación manual por el superadmin

Que el superadmin marque los correos como verificados. Se descarta porque **no verifica nada**: el superadmin no tiene forma de saber si esa casilla existe ni quién la controla. Daría una sensación de seguridad sin ninguna garantía detrás.

## Consecuencias

### Positivas

- Una institución ya no puede quedar sin acceso a su cuenta de gestión.
- El correo pasa de ser un dato decorativo a una vía de acceso comprobada.
- El mecanismo queda disponible para futuros usos (confirmar operaciones sensibles, segundo factor).
- `usuarios.ultimo_login` y `codigos_verificacion.ip_solicitud` dan trazabilidad de quién pidió qué y desde dónde (RNF-10).

### Negativas y limitaciones

- **Aparece una dependencia de infraestructura nueva**: sin un SMTP disponible no hay recuperación posible. La aplicación avisa del fallo en vez de decir "revisá tu correo", pero la persona queda sin poder recuperar hasta que el servicio vuelva.
- Un OTP por correo **no es un segundo factor**: si alguien ya controla el buzón, controla la cuenta. La seguridad se apoya enteramente en la del correo institucional.
- El límite de cinco pedidos por hora es por cuenta, no por IP: no frena a quien quiera pedir códigos de muchas cuentas distintas.
- Las cuentas sembradas por la migración `V002` quedan **sin verificar** a propósito. Nadie confirmó esas casillas, y darlas por buenas vaciaría de sentido a la propia verificación.

## Referencias

- [ADR-0003: Estrategia de sesión — cookie HTTP clásica](./0003-sesion-cookie-vs-jwt.md) — el flujo público se apoya en la sesión para no exponer el id en la URL.
- Migración `V007__codigos_verificacion_email.sql` — esquema y comentarios de cada decisión.
- Ley 25.326 de Protección de los Datos Personales — fundamento del descarte de servicios externos.
