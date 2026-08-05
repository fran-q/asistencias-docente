# Normalización de la base de datos

> Revisión de las 15 tablas contra 1FN, 2FN y 3FN, hecha sobre el esquema real
> (`information_schema`) y comprobando las dependencias sospechadas **contra los datos**,
> no solo leyendo el DDL. Al final: qué se defiende y qué no.

---

## Resumen

| Forma | Estado |
|---|---|
| **1FN** — valores atómicos, sin grupos repetitivos | ✅ Se cumple en las 15 tablas |
| **2FN** — sin dependencias parciales | ✅ Se cumple **trivialmente** (ver abajo) |
| **3FN** — sin dependencias transitivas | ⚠️ Cuatro excepciones: tres deliberadas y defendibles, una corregida |

---

## 1FN — Primera forma normal

Se cumple. No hay campos multivaluados, listas separadas por comas ni grupos repetitivos.

Los dos campos `TEXT` que existen —`asistencias_manuales.detalle_adicional` y
`justificaciones_ausencia.motivo`— son texto libre escrito por una persona, no una lista
de valores disfrazada. Un motivo de justificación es **un** motivo, aunque sea largo.

---

## 2FN — Segunda forma normal

Se cumple, y conviene saber **por qué** para poder defenderlo bien.

La 2FN prohíbe que un atributo dependa solo de *parte* de la clave primaria. Eso únicamente
puede ocurrir con **claves primarias compuestas**.

En este esquema **las 15 tablas tienen clave primaria simple**: un `id` autoincremental.
Sin clave compuesta no puede haber dependencia parcial, así que la 2FN se cumple por
construcción.

> **Si te preguntan "¿y por qué no usaste claves naturales compuestas?"** — por ejemplo
> `(institucion_id, dni)` como PK de docentes: porque las claves naturales cambian. Un DNI
> se carga mal y se corrige, y con una PK natural esa corrección obliga a actualizar todas
> las filas que la referencian. Las unicidades naturales sí están, pero como
> `UNIQUE`, no como PK: `uq_docentes_inst_dni`, `uq_materias_inst_codigo`,
> `uq_comisiones_materia_codigo`.

---

## 3FN — Tercera forma normal

Acá están las excepciones. La 3FN prohíbe que un atributo no-clave dependa de otro
atributo no-clave. Encontré cuatro casos, y **no todos son lo mismo**.

### 3.1 `institucion_id` repetido — desnormalización deliberada ✅ defendible

Tres tablas guardan `institucion_id` aunque se pueda deducir siguiendo las claves foráneas:

| Tabla | Se podría deducir por |
|---|---|
| `materias` | `carrera_id → carreras.institucion_id` |
| `asistencias` | `docente_id → docentes.institucion_id` |
| `codigos_verificacion` | `usuario_id → usuarios.institucion_id` |

**Es una violación de 3FN, y es intencional.** El aislamiento multi-tenant se aplica con un
filtro de Hibernate que agrega `WHERE institucion_id = ?` a cada consulta. Para que ese
filtro funcione, la columna tiene que estar **en la propia tabla**: si hubiera que llegar a
ella por un JOIN, el filtro no podría aplicarse de forma automática y cada repositorio
tendría que acordarse de sumar la condición a mano.

El costo de olvidarse una vez es que una institución vea los datos de otra. La redundancia
compra que sea imposible olvidarse.

> **Cómo defenderlo:** "Sí, viola 3FN, y está hecho a propósito. La normalización es una
> guía para evitar anomalías de actualización; acá la anomalía que evitamos es más grave
> que la que introducimos. Además `institucion_id` de una fila nunca cambia: un docente no
> se muda de institución, se da de baja en una y se crea en otra."

El comentario está en el propio DDL desde V001: *"Denormalizado para reforzar aislamiento
multi-tenant"*.

### 3.2 `consentimientos_biometricos.vigente` — campo derivado ⚠️ discutible

`vigente` es exactamente `fecha_revocacion IS NULL`. **Comprobado contra los datos: las 5
filas coinciden al 100 %.**

Es una dependencia derivada y por lo tanto viola 3FN. La defensa posible es que se consulta
constantemente —cada registro de rostro pregunta si el consentimiento está vigente— y una
columna booleana indexable es más barata que evaluar `IS NULL` sobre un timestamp.

> **Cómo defenderlo, con honestidad:** "Es redundante y lo sé. Se mantiene porque es la
> condición que más se consulta en todo el sistema y porque el modelo la mantiene en un
> solo lugar. El riesgo es que se desincronice, y por eso `vigente` nunca se escribe desde
> afuera del servicio que también escribe la fecha."

Si te lo señalan como defecto, aceptarlo es mejor que defenderlo con vehemencia.

### 3.3 `asistencias.comision_id` — derivable del horario ⚠️ discutible

`comision_id` se puede deducir de `horario_id → horarios.comision_id`. **Comprobado: las 4
asistencias coinciden con la comisión de su horario.**

La defensa es que el reporte agrupa y filtra por comisión y por materia, y tenerla en la
misma fila ahorra un JOIN en la consulta más pesada del sistema.

> **El riesgo concreto que introduce:** si un horario se reasigna a otra comisión, las
> asistencias ya registradas quedarían apuntando a la comisión vieja. Hoy eso **no puede
> pasar**, porque `HorarioService.actualizar` permite cambiar la comisión de un horario.
> **Es una anomalía real, no teórica.**

Es el punto más débil del esquema y conviene tenerlo identificado antes de que te lo
encuentren.

### 3.4 `modelos_faciales`: `creado_en` y `fecha_registro` — ✅ **corregido**

Las dos columnas registraban lo mismo: cuándo se dio de alta el modelo. La V012 agregó
`creado_en` sin advertir que `fecha_registro` ya existía.

**No era teórico: los valores ya estaban desincronizados.** `fecha_registro` tenía la fecha
real de cada modelo y `creado_en` la fecha en que corrió la migración, así que dos consultas
a la misma pregunta daban respuestas distintas.

Corregido en **V013**, que elimina `creado_en`. Se conservó `fecha_registro` porque tiene
los datos buenos y porque es el nombre que usa la constancia ARCO.

---

## Otras cosas que revisé

### `activo` + `fecha_baja`: ¿es redundante?

**No.** `activo = 0` con `fecha_baja = NULL` es un estado real y significa "se dio de baja
antes de que el sistema registrara la fecha". Existe en las filas anteriores a V008 y V012.
Los dos campos juntos dicen más que cualquiera por separado.

### `consentimientos_biometricos` mezcla dos eventos

La misma fila guarda el otorgamiento (fecha, IP, user-agent, quién) y la revocación (fecha,
IP, user-agent, quién, motivo). Cuando no hay revocación, cinco columnas quedan en `NULL`.

Cumple 3FN —todo depende de la PK— pero es un diseño que se puede cuestionar: son dos
hechos distintos en un solo registro.

> **Cómo defenderlo:** el consentimiento es **una** relación entre docente e institución que
> tiene un ciclo de vida, no dos hechos sueltos. La alternativa —una tabla de eventos— haría
> que la pregunta más frecuente ("¿está vigente?") requiriera buscar el último evento y
> mirar su tipo. Se eligió el modelo más simple para la consulta más común.

### Lo que NO está de más

Revisé si algo sobraba y estas tres, que a primera vista parecen accesorias, se defienden:

| Campo | Por qué está |
|---|---|
| `asistencias.confianza` | Es el dato con el que se calibró el umbral del reconocimiento. Se sacó del reporte porque al administrador no le dice nada, pero en la base es la única evidencia de cómo se decidió el valor. |
| `codigos_verificacion.ip_solicitud` | Permite detectar quién está pidiendo códigos en masa. Sin él, el freno de envíos no se puede auditar. |
| `horarios.tolerancia_min` | Por horario y no global: una clase de las 7 de la mañana y una de la noche no toleran lo mismo. |

### `roles` sin `activo` ni `fecha_baja`

Correcto y a propósito. Es un catálogo fijo del sistema —INSTITUCION, ADMIN—, no algo que
la institución dé de alta y de baja. Ponerle esas columnas sugeriría una operación que no
existe.

---

## Qué decir si te preguntan "¿tu base está normalizada?"

> "Está en tercera forma normal, con tres excepciones deliberadas que puedo justificar y
> una que era un error y corregí.
>
> Las deliberadas son la columna `institucion_id` repetida en tres tablas, que es lo que
> hace posible el aislamiento multi-tenant automático; un booleano `vigente` que duplica
> una condición que se consulta en cada registro de rostro; y la comisión guardada en la
> asistencia para no hacer un JOIN en el reporte.
>
> La tercera era la más débil: si un horario cambiaba de comisión, las asistencias
> viejas quedaban apuntando a la anterior. Lo cerré prohibiendo ese cambio en
> `HorarioService`: para mover una clase hay que dar de baja el horario y crear uno
> nuevo. La redundancia quedó, el riesgo no."

Esa respuesta vale más que decir "sí, está normalizada", porque demuestra que sabés dónde
están los costos de cada decisión.
