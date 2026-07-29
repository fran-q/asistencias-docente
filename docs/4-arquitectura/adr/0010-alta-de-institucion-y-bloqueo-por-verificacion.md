# ADR-0010: Alta de institución con clave de instalación y bloqueo de cuentas sin verificar

**Estado**: Aceptada
**Fecha**: 2026-07-29
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

[ADR-0009](./0009-verificacion-correo-y-recuperacion.md) construyó el mecanismo de verificación de correo, pero lo dejó **opcional**: una cuenta sin verificar podía operar el sistema completo. La marca `usuarios.email_verificado_en` existía, se llenaba correctamente y no impedía nada. Verificar era una buena costumbre, no un requisito.

En paralelo apareció un segundo problema, detectado al intentar dar de alta usuarios reales durante las pruebas: **no había forma de crear una institución desde la aplicación**. Las dos existentes venían sembradas por la migración `V002`. Para sumar una tercera había que escribir `INSERT` a mano contra la base, y después crear su primer usuario también a mano, con el hash BCrypt calculado por fuera. Eso no es un procedimiento que pueda documentarse en un manual de instalación.

Los dos problemas están enlazados por una pregunta: **¿quién tiene derecho a crear la primera cuenta de una institución?** No puede ser un rol, porque el alta ocurre antes de que exista nadie con ese rol. Y si esa primera cuenta tuviera que verificarse por correo, una institución sin servidor SMTP disponible nacería sin acceso a su propia cuenta de gestión.

## Decisiones

### 1. El alta de institución se protege con una clave de instalación, no con un rol

`/alta-institucion` es, junto con el login y la recuperación, una ruta accesible sin sesión. Lo que la protege es una clave que sale de una variable de entorno (`app.instalacion.clave`, alimentada por `INSTALACION_CLAVE`) y que quien instala el sistema conoce.

El motivo es que **no hay ningún rol que pueda autorizar esta operación**: la institución todavía no existe, así que no existe tampoco ningún usuario dentro de ella. Cualquier otra ruta del sistema se apoya en el `TenantContext`; ésta es la única que corre con el contexto vacío, por definición.

De ahí se desprenden dos consecuencias que quedan explícitas en el código:

- El servicio **no puede apoyarse en el filtro de Hibernate** para el aislamiento, porque no hay tenant al cual filtrar. La unicidad del nombre y del CUIT se valida a mano, contra toda la tabla.
- La comparación de la clave usa `MessageDigest.isEqual`, que **compara en tiempo constante**. Una comparación con `equals` corta en el primer carácter distinto, y esa diferencia de microsegundos permite, con suficientes intentos, adivinar la clave carácter por carácter.

Si la variable no está configurada, el alta queda **deshabilitada** y lo dice: es preferible a que quede abierta con una clave vacía.

### 2. La institución y su primera cuenta se crean en una sola transacción

Una institución sin ninguna cuenta con la cual entrar es inservible, y además queda ocupando el nombre y el CUIT, que son únicos en todo el sistema. Si el alta pudiera cortarse por la mitad, el segundo intento chocaría contra el registro huérfano del primero.

### 3. El nombre y el CUIT de una institución son únicos en todo el sistema

A diferencia de casi todo el resto del modelo, esta unicidad **no es por tenant sino global**. Dos colegios distintos no pueden llamarse igual ni compartir CUIT: el nombre es lo que identifica a la institución frente a quien administra el despliegue, y un CUIT repetido significa directamente que uno de los dos está mal cargado.

Es el contraste deliberado con el punto siguiente.

### 4. El correo y el usuario de una persona son únicos por institución, no globalmente

La misma persona puede tener cuenta de administrador en una institución **y** ser docente en otra, con la misma dirección de correo. Es la situación normal en el sistema educativo provincial, donde el personal suele trabajar en más de un establecimiento.

Forzar unicidad global de correo obligaría a esa persona a inventarse una dirección por institución, que es exactamente el tipo de dato falso que [ADR-0009](./0009-verificacion-correo-y-recuperacion.md) buscaba evitar.

### 5. La primera cuenta de la institución nace verificada

No se le pide confirmar el correo. **Quien la crea ya demostró conocer la clave de instalación**, que es una prueba de autorización más fuerte que un código enviado por correo.

Además es lo que evita el peor escenario posible: si esa cuenta tuviera que verificarse y el SMTP no estuviera disponible, la institución quedaría encerrada fuera de su propia cuenta de gestión sin ningún camino de vuelta — precisamente el problema que ADR-0009 se propuso cerrar.

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

### Registro de institución abierto, sin clave

Que cualquiera pueda registrar una institución, como en un servicio comercial. Se descarta porque el sistema **no es un servicio público**: se despliega dentro de una institución educativa. Un alta abierta permitiría llenar la base de instituciones falsas, y cada una arrastra una cuenta con acceso al sistema.

### Bloquear con un filtro de Spring Security en vez de un interceptor

Técnicamente posible y en algún sentido más ortodoxo. Se descarta porque el filtro corre **antes** que `TenantInterceptor`, y entonces la relectura del punto 7 quedaría fuera del contexto de tenant. Habría que duplicar la resolución del tenant dentro del filtro, es decir, mantener dos copias de la misma lógica.

### Deshabilitar la cuenta (`activo = false`) hasta que verifique

Reutiliza un campo que ya existe y Spring Security la rechazaría en el login sin escribir nada. Se descarta porque **encierra a la persona afuera**: si no puede iniciar sesión, tampoco puede llegar a la pantalla donde pediría su código. Y confunde dos cosas distintas: `activo = false` significa "esta cuenta fue dada de baja", no "todavía no confirmó su correo".

## Consecuencias

### Positivas

- Instalar el sistema en una institución nueva es un procedimiento de aplicación, no de base de datos.
- La verificación de correo pasa de recomendación a requisito efectivo, y con ella la garantía de que toda cuenta operativa tiene una vía de recuperación real.
- El bloqueo por lista blanca hace que las pantallas futuras nazcan protegidas.
- Una persona que trabaja en varias instituciones puede usar su correo real en todas.

### Negativas y limitaciones

- **La clave de instalación es un secreto compartido**: no rota, no expira y no distingue quién la usó. El registro queda en el log (`Alta de institucion: institucion_id=...`), pero la clave en sí no identifica a nadie.
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
