# ADR-0011: Traducción de los errores de integridad de la base

**Estado**: Aceptada
**Fecha**: 2026-07-29
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Cada servicio valida la unicidad antes de guardar: `DocenteService` consulta `existsByDni` antes del `save`, `UsuarioService` consulta `existsByUsernameAndInstitucionId`, y así con carreras, materias y comisiones. Esas validaciones producen buenos mensajes y —lo que importa tanto como el mensaje— devuelven a la persona al formulario **con lo que ya había tipeado**.

Pero esa comprobación previa no es una garantía. Entre el `SELECT` que pregunta si el DNI existe y el `INSERT` que lo guarda hay una ventana: si dos administrativos cargan el mismo docente al mismo tiempo, los dos preguntan, los dos reciben "no existe", y el segundo choca contra el índice único. Cuando eso pasa, la `DataIntegrityViolationException` sube sin que nadie la atienda y termina en la pantalla de error genérica de Spring, con un texto que menciona el nombre del índice y el valor que se intentó guardar.

La ventana es angosta y el escenario poco frecuente, pero el problema real no es la carrera: es que **la corrección del sistema depende de que nadie olvide una validación**. Una pantalla nueva que guarde sin preguntar antes hereda el error 500 sin que nada lo señale.

## Decisiones

### 1. Dos niveles, no uno

Se mantiene la validación en cada servicio **y** se agrega un manejador global. No son alternativas:

| Nivel | Qué aporta | Qué no puede hacer |
|---|---|---|
| Validación en el servicio | Mensaje preciso, y el formulario se vuelve a dibujar con lo tipeado | No cubre lo que se le escapa |
| Manejador global | Cubre cualquier camino, presente o futuro | No sabe qué formulario era, así que redirige y se pierde lo tipeado |

El costo de llegar al segundo nivel es real —perder lo cargado molesta— y por eso el primero sigue siendo el camino normal. El segundo existe para que el peor caso sea "tuve que volver a escribir" y no "apareció una pantalla de error".

Es el mismo criterio de la defensa en tres capas del aislamiento multi-tenant ([ADR-0004](./0004-tenant-filter-en-joins.md)): una capa que actúa siempre, aunque casi nunca haga falta.

### 2. La traducción se hace por nombre de restricción

El motor devuelve `Duplicate entry '1-30111222' for key 'uq_docentes_inst_dni'`. Ese texto no sirve para mostrar, pero el nombre del índice identifica **exactamente** qué se chocó, así que el manejador tiene un mapa de nombre de restricción a explicación.

La consecuencia práctica es que **el nombre de un índice pasa a ser parte del contrato**: renombrar `uq_docentes_inst_dni` en una migración sin actualizar el mapa degrada silenciosamente el mensaje al genérico. Queda anotado acá porque no es evidente leyendo la migración.

Se aprovechó para que cada mensaje diga **en qué ámbito** el dato no se puede repetir, que es lo que la persona necesita para entender qué pasó: el DNI de un docente es único dentro de la institución, el nombre de una institución es único en todo el sistema, y el correo de una cuenta es único por institución pero puede repetirse entre instituciones ([RF-43](../../2-requerimientos/01-requerimientos.md)).

### 3. Una restricción sin traducir cae en un mensaje general

Si aparece un nombre que no está en el mapa, se responde con un texto genérico. Se descartó mostrar el mensaje del motor como último recurso: expone nombres de tablas e índices y el valor concreto que se intentó guardar, que en estas tablas son datos personales.

### 4. El destino de la redirección se valida contra el propio servidor

El manejador vuelve a la pantalla anterior leyendo el `Referer`, y **solo lo acepta si apunta a este mismo servidor**.

Sin ese control, el `Referer` sería un redirector abierto: una página externa podría provocar el error y llevar a alguien —con la sesión ya iniciada— a un formulario ajeno que imite al nuestro. Es una cabecera que la manda el cliente, así que no se le puede creer.

### 5. Al cliente que pide datos no se le manda una redirección

El pase de asistencia consulta con `fetch` y espera JSON. Una redirección a HTML le rompe el `resp.json()` con un error que no explica nada, así que el manejador no atiende esos pedidos y deja que el error siga su curso hasta un código de error, que es lo que ese cliente sabe leer.

Para distinguirlos no alcanza con mirar el `Accept`: `fetch()` manda `*/*` salvo que se le indique otra cosa. Se mira también el `Content-Type` del pedido, porque quien manda JSON en el cuerpo es un cliente de datos aunque no lo declare al pedir.

## Alternativas descartadas

### Solo validar en los servicios, sin manejador global

Es lo que había. Se descarta porque hace que la corrección dependa de que nadie olvide una validación, y porque el olvido no da ninguna señal hasta que un usuario ve la pantalla de error.

### Solo el manejador global, sacando las validaciones de los servicios

Menos código y una única fuente de mensajes. Se descarta porque **se pierde lo que la persona había tipeado en cada choque**: el manejador redirige, y desde ahí no hay forma de saber qué formulario era ni con qué valores. Además dejaría al servicio sin ninguna regla propia, lo que hace más difícil probarlo sin base de datos.

### Capturar la excepción en cada controlador

Es lo que ya hacía `MiInstitucionController`. Funciona, pero repite el mismo bloque en cada pantalla y vuelve al problema de origen: la pantalla nueva nace sin él. Ese controlador conserva su captura local —ahí sí conviene, porque conserva lo tipeado— pero delega el texto en el mismo traductor, así que hay un solo lugar donde se decide qué dice cada restricción.

## Consecuencias

### Positivas

- Ningún choque contra la base puede terminar en una pantalla de error genérica, venga del camino que venga.
- Los mensajes explican el ámbito de la unicidad, que es la parte que confunde en un sistema multi-institución.
- Una pantalla futura que guarde sin validar antes degrada el mensaje, pero no rompe.

### Negativas y limitaciones

- **Los nombres de los índices quedan acoplados al código.** Una migración que renombre uno degrada el mensaje sin avisar.
- Cuando actúa el manejador global se pierde lo cargado en el formulario. Es el precio de no saber, desde ahí, qué pantalla era.
- El mapa hay que mantenerlo: cada restricción única nueva necesita su entrada, y nada obliga a agregarla.

## Referencias

- [ADR-0004: Defensa en profundidad multi-tenant](./0004-tenant-filter-en-joins.md) — mismo criterio de capas que actúan aunque la anterior ya cubra el caso.
- `ManejadorDeColisiones` — el mapa de restricciones y la validación del destino de la redirección.
- `ManejadorDeColisionesTest` y `ManejadorDeColisionesIT` — traducción de cada restricción y comportamiento HTTP.
- Migración `V001__init.sql` — definición de los índices únicos cuyos nombres usa el mapa.
