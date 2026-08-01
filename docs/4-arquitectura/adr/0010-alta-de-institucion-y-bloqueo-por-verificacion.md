# ADR-0010: Alta de institución por código y bloqueo de cuentas sin verificar

**Estado**: Aceptada
**Fecha**: 2026-07-29 · **Revisada**: 2026-07-31
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

[ADR-0009](./0009-verificacion-correo-y-recuperacion.md) construyó el mecanismo de verificación de correo, pero lo dejó **opcional**: una cuenta sin verificar podía operar el sistema completo. La marca `usuarios.email_verificado_en` existía, se llenaba correctamente y no impedía nada. Verificar era una buena costumbre, no un requisito.

En paralelo apareció un segundo problema, detectado al intentar dar de alta usuarios reales durante las pruebas: **no había forma de crear una institución desde la aplicación**. Las dos existentes venían sembradas por la migración `V002`. Para sumar una tercera había que escribir `INSERT` a mano contra la base, y después crear su primer usuario también a mano, con el hash BCrypt calculado por fuera. Eso no es un procedimiento que pueda documentarse en un manual de instalación.

Los dos problemas están enlazados por una pregunta: **¿quién tiene derecho a crear la primera cuenta de una institución?** No puede ser un rol, porque el alta ocurre antes de que exista nadie con ese rol.

La primera versión respondió con una clave de instalación fija. La revisión del 31/07 la reemplazó por el código de un solo uso, para que el sistema no tenga dos mecanismos de validación distintos conviviendo.

## Decisiones

### 1. El alta se valida con el mismo código de un solo uso que el resto del sistema

`/alta-institucion` es, junto con el login y la recuperación, una ruta accesible sin sesión. Lo que la protege es un **código de seis dígitos enviado al correo declarado**: el mismo mecanismo, con las mismas defensas, que usan la verificación de cuenta y la recuperación de contraseña.

El motivo de que no la proteja un rol es que **no hay ningún rol que pueda autorizar esta operación**: la institución todavía no existe, así que no existe tampoco ningún usuario dentro de ella. Cualquier otra ruta del sistema se apoya en el `TenantContext`; ésta es la única que corre con el contexto vacío, por definición.

De ahí se desprende que el servicio **no puede apoyarse en el filtro de Hibernate** para el aislamiento, porque no hay tenant al cual filtrar. La unicidad del nombre y del CUIT se valida a mano, contra toda la tabla, y se vuelve a validar al confirmar: entre el envío del código y su validación pasan minutos, y en el medio otra persona pudo haber registrado ese mismo nombre.

> **Versión anterior.** Hasta el 31/07/2026 el alta se protegía con una clave de instalación fija (`INSTALACION_CLAVE`) y la primera cuenta nacía verificada sin código. Se reemplazó para que el sistema tenga **un solo mecanismo de validación** en vez de dos, y porque la clave era un secreto compartido que no rotaba, no vencía y no identificaba quién la había usado.

### 2. La institución y su primera cuenta se crean en una sola transacción

Una institución sin ninguna cuenta con la cual entrar es inservible, y además queda ocupando el nombre y el CUIT, que son únicos en todo el sistema. Si el alta pudiera cortarse por la mitad, el segundo intento chocaría contra el registro huérfano del primero.

### 3. El nombre y el CUIT de una institución son únicos en todo el sistema

A diferencia de casi todo el resto del modelo, esta unicidad **no es por tenant sino global**. Dos colegios distintos no pueden llamarse igual ni compartir CUIT: el nombre es lo que identifica a la institución frente a quien administra el despliegue, y un CUIT repetido significa directamente que uno de los dos está mal cargado.

Es el contraste deliberado con el punto siguiente.

### 4. El correo y el usuario de una persona son únicos por institución, no globalmente

La misma persona puede tener cuenta de administrador en una institución **y** ser docente en otra, con la misma dirección de correo. Es la situación normal en el sistema educativo provincial, donde el personal suele trabajar en más de un establecimiento.

Forzar unicidad global de correo obligaría a esa persona a inventarse una dirección por institución, que es exactamente el tipo de dato falso que [ADR-0009](./0009-verificacion-correo-y-recuperacion.md) buscaba evitar.

### 5. Nada se crea hasta que el código se valida

El formulario no persiste nada: manda el código y deja los datos **en espera dentro de la sesión** del navegador. La institución y su cuenta se crean recién al validar.

Esto resuelve de raíz el problema que tenía la versión anterior. Si la institución se creara primero y quedara bloqueada hasta verificar, un correo que nunca llega dejaría una institución existente, inutilizable y **ocupando su nombre y su CUIT**, que después hay que limpiar a mano desde la base. Al no crear nada, un alta abandonada no deja rastro.

La cuenta nace verificada, pero ahora por un motivo distinto y más sólido: **acaba de demostrar que controla esa casilla**, que es exactamente lo que la verificación pide. No es una excepción a la regla, es la regla ya cumplida.

Los datos en espera viven en la sesión y no en la base porque no hay institución ni usuario a los cuales asociarlos, y su vida útil son los quince minutos que dura el código. Persistirlos habría significado una tabla paralela para algo que pertenece a un solo navegador durante un rato.

### 6. Todas las demás cuentas quedan bloqueadas hasta verificar

Un `VerificacionInterceptor` retiene cualquier petición de una cuenta con `email_verificado_en` en nulo y la manda a `/mi-cuenta?verificacion-requerida`.

El bloqueo es **por defecto denegar**: no hay una lista de rutas protegidas, sino una lista muy corta de rutas permitidas.

| Ruta permitida | Por qué |
|---|---|
| `/mi-cuenta` | Es donde se desbloquea |
| `/mi-cuenta/enviar-codigo` | Pedir el código |
| `/mi-cuenta/verificar` | Ingresarlo |
| `/logout` | Nadie puede quedar atrapado dentro de una sesión |

Una lista de rutas prohibidas envejecería mal: cada pantalla nueva nacería sin protección hasta que alguien se acordara de agregarla. Con la lista invertida, una pantalla nueva nace bloqueada, que es el error seguro.

### 7. Verificar desbloquea en la misma sesión, sin volver a iniciarla

El `CustomUserDetails` se arma **al iniciar sesión**, así que la marca de verificación que lleva es una foto del momento del login: siempre en falso para quien todavía no verificó. Si el interceptor se apoyara solo en esa foto, la persona verificaría su correo correctamente y **seguiría bloqueada** hasta cerrar y volver a abrir sesión, sin ninguna explicación visible.

Por eso el interceptor primero mira la marca del principal —camino rápido, sin consultar la base, que es el caso del 99 % de las peticiones— y solo si está en falso vuelve a leer el usuario de la base. Si ahí figura verificado, actualiza el principal en memoria y deja pasar.

### 8. El orden de los interceptores es parte de la decisión

`TenantInterceptor` corre primero (`order(1)`) y `VerificacionInterceptor` después (`order(2)`). La razón es concreta: la relectura del usuario del punto anterior es una consulta a la base, y esa consulta tiene que correr con la institución ya publicada en el `TenantContext`. Invertido, la lectura caería fuera del filtro multi-tenant.

## Alternativas descartadas

### Un rol de superadministrador global que cree instituciones

Sería lo natural en un sistema con administración centralizada. Se descarta porque **rompe el modelo de aislamiento** descrito en [ADR-0002](./0002-multi-tenant-discriminator.md): habría que introducir un usuario sin `institucion_id`, y con él una excepción permanente al filtro de Hibernate que hoy no admite ninguna. Un solo camino que esquive el filtro es un camino que hay que auditar para siempre.

### Mantener la clave de instalación además del código

Sería la opción más cerrada: la clave decide quién puede crear y el código prueba el correo. **Se evaluó y se descartó deliberadamente**, a favor de tener un único mecanismo de validación en todo el sistema.

Hay que ser explícito sobre lo que eso cuesta: **el alta queda abierta**. Cualquiera que alcance la pantalla y tenga una casilla de correo puede crear una institución, y cada una arrastra una cuenta con acceso. Lo que queda como defensa es que el correo tiene que ser real y que hay un tope de envíos por dirección.

Mientras el despliegue viva en una red interna esto es aceptable. **Si alguna vez se publica en internet, hay que reponer una barrera**: restringir la ruta por IP de origen o volver a exigir una credencial. Queda anotado como la condición que vuelve necesaria esa revisión.

### Bloquear con un filtro de Spring Security en vez de un interceptor

Técnicamente posible y en algún sentido más ortodoxo. Se descarta porque el filtro corre **antes** que `TenantInterceptor`, y entonces la relectura del punto 7 quedaría fuera del contexto de tenant. Habría que duplicar la resolución del tenant dentro del filtro, es decir, mantener dos copias de la misma lógica.

### Deshabilitar la cuenta (`activo = false`) hasta que verifique

Reutiliza un campo que ya existe y Spring Security la rechazaría en el login sin escribir nada. Se descarta porque **encierra a la persona afuera**: si no puede iniciar sesión, tampoco puede llegar a la pantalla donde pediría su código. Y confunde dos cosas distintas: `activo = false` significa "esta cuenta fue dada de baja", no "todavía no confirmó su correo".

## Consecuencias

### Positivas

- Instalar el sistema en una institución nueva es un procedimiento de aplicación, no de base de datos.
- **Un solo mecanismo de validación en todo el sistema**: la misma pantalla, el mismo formato de código y las mismas defensas para el alta de institución, la verificación de cuenta y la recuperación de contraseña. Hay una sola cosa que entender y una sola que auditar.
- Un alta abandonada no deja nada: ni institución, ni cuenta, ni un nombre ocupado.
- La verificación de correo pasa de recomendación a requisito efectivo, y con ella la garantía de que toda cuenta operativa tiene una vía de recuperación real.
- El bloqueo por lista blanca hace que las pantallas futuras nazcan protegidas.
- Una persona que trabaja en varias instituciones puede usar su correo real en todas.

### Negativas y limitaciones

- **El alta es abierta.** No hay ninguna barrera previa al correo: quien llegue a la pantalla puede iniciar un alta. El tope de envíos por dirección evita que la pantalla se use para molestar a una casilla ajena, pero no impide que alguien cree instituciones con correos propios. Es la contrapartida directa de unificar el mecanismo, y solo es aceptable mientras el despliegue no esté expuesto a internet.
- **El freno de envíos vive en memoria**: se reinicia con la aplicación y no se comparte entre instancias. Alcanza para el abuso casual, no para un intento sostenido.
- Los datos en espera viven en la sesión: si se cierra el navegador antes de validar, hay que volver a completar el formulario.
- Las cuentas sembradas por `V002` quedan bloqueadas hasta verificar. Es intencional —darlas por buenas vaciaría de sentido la verificación— pero significa que un despliegue nuevo necesita el SMTP funcionando para que esas cuentas puedan operar. La salida está documentada más abajo.
- El interceptor agrega **una consulta a la base por petición** mientras la cuenta esté sin verificar. Es una situación transitoria y acotada a una sola cuenta, pero no es gratis.
- La unicidad global del nombre de institución es sensible al tipeo: "Escuela N° 7" y "Escuela Nro 7" conviven sin conflicto.

## Salida de emergencia

Si una cuenta queda bloqueada y el servidor de correo no está disponible, se la puede verificar directamente en la base:

```sql
UPDATE usuarios SET email_verificado_en = NOW() WHERE username = 'el.usuario';
```

El desbloqueo es inmediato y **no requiere cerrar sesión**, por lo explicado en la decisión 7.

Esto es una intervención manual sobre la base y debe registrarse como tal: saltea la comprobación de que esa casilla existe y es de esa persona, que es justamente lo que el mecanismo garantiza. Se justifica solo cuando el SMTP está caído y hay una urgencia operativa. La alternativa correcta es levantar el servidor de correo.

Para verificar de una sola vez las cuentas sembradas por `V002` en un despliegue de demostración:

```sql
UPDATE usuarios SET email_verificado_en = NOW() WHERE email_verificado_en IS NULL;
```

## Referencias

- [ADR-0002: Multi-tenant por discriminador](./0002-multi-tenant-discriminator.md) — el modelo de aislamiento que el alta no puede romper.
- [ADR-0009: Verificación de correo y recuperación](./0009-verificacion-correo-y-recuperacion.md) — el mecanismo que esta decisión vuelve obligatorio.
- `AltaInstitucionService` — alta transaccional y comparación en tiempo constante de la clave.
- `VerificacionInterceptor` — lista blanca de rutas y relectura del estado de verificación.
- `AltaInstitucionIT` y `VerificacionObligatoriaIT` — pruebas de las dos mitades.
