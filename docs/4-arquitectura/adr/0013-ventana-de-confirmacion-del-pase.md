# ADR-0013: Ventana de confirmación antes de marcar asistencia

**Estado**: Aceptada
**Fecha**: 2026-07-30
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Con tres modelos faciales cargados —incluidos dos hermanos y su madre— el reconocimiento funcionó bien y los diferenció correctamente, que es la prueba más exigente posible para LBPH: el parecido familiar es justamente donde un descriptor de texturas tiene menos margen.

Apareció, sin embargo, un fallo reproducible: **cuando cambia la iluminación, el reconocimiento oscila** entre dos personas parecidas. Un cuadro identifica a una, el siguiente a la otra.

Hasta ahora el pase marcaba asistencia **con un solo cuadro**: el navegador enviaba una imagen por segundo y cada respuesta reconocida disparaba la marca. Con la identidad oscilando, eso significa que **el primer cuadro que salga ganador escribe la asistencia**, aunque el siguiente diga otra cosa.

La asimetría del error es lo que vuelve esto importante. Una marca no registrada se resuelve con la carga manual, que ya existe y deja constancia de quién la hizo y por qué. Una marca **equivocada** queda asentada como un hecho: alguien figura habiendo dado una clase que no dio. Detectarla requiere que alguien la note, y corregirla, que alguien la justifique.

## Decisión

**El pase solo marca cuando la misma identidad se sostiene tres segundos.**

Mientras tanto la pantalla muestra el recuadro y una cuenta regresiva, sin el nombre. Si en ese lapso aparece otro docente, la cuenta vuelve a cero.

### Por qué la cuenta se corta al cambiar de persona

Es el corazón de la decisión y el escenario exacto que se busca frenar. Si la racha simplemente acumulara lecturas sin importar de quién, dos personas parecidas alternándose terminarían confirmando a alguna de las dos. Al reiniciar, **oscilar no confirma a nadie**: la marca no se produce hasta que la ambigüedad se resuelva sola, que es lo que pasa cuando la persona se acomoda o la luz se estabiliza.

### Por qué el nombre no se muestra durante la confirmación

Mostrarlo haría aparecer y desaparecer el nombre equivocado en pantalla mientras el reconocimiento oscila, que es precisamente el síntoma que se quiere ocultar. Peor todavía: le daría al operador la impresión de que el sistema "dudó" entre dos personas, cuando la respuesta correcta es simplemente esperar.

### Por qué se tolera un cuadro perdido

La racha se corta si pasan más de 2,5 segundos sin lecturas, no ante cualquier hueco. El navegador envía un cuadro por segundo, pero una petición lenta o un cuadro sin rostro detectado son normales con una cámara real. Exigir continuidad perfecta haría que la confirmación **nunca se complete**, y el sistema quedaría inutilizable por ser demasiado estricto.

### Por qué el estado vive en el servidor

La racha se guarda en la sesión HTTP del operador, no en el navegador.

Si el conteo viviera en JavaScript, saltear el control sería editar una variable desde la consola del navegador. Es el mismo criterio que ya se aplica en la captura guiada del rostro (ADR-0012): **el cliente decide cuándo pedir, el servidor decide si corresponde**.

La sesión, además, se limpia sola cuando expira. Un mapa en memoria indexado por sesión habría requerido su propia purga de entradas muertas.

## Alternativas descartadas

### Subir el umbral de confianza

La reacción intuitiva ante "confunde dos personas" es exigir más parecido. Se descarta porque **cambia un error por otro**: con un umbral más estricto, las mismas condiciones de luz que hoy producen una confusión pasarían a producir un "no reconocido", y el docente quedaría sin marcar. No resuelve la inestabilidad, solo elige de qué lado fallar.

Además, el umbral es global: endurecerlo por dos personas parecidas degrada el reconocimiento de todos los demás.

### Exigir un margen mínimo respecto del segundo candidato

Descartar el reconocimiento cuando el segundo mejor está demasiado cerca. Es una buena idea y **sigue disponible como complemento**, pero por sí sola no alcanza: bajo iluminación cambiante el margen también oscila, así que habría cuadros con margen amplio a favor de la persona equivocada. La consistencia temporal cubre ese caso porque no depende de la calidad de un cuadro sino de la coincidencia de varios.

### Promediar las distancias de varios cuadros

Identificar contra el promedio en vez de contra cada cuadro. Se descarta por complejidad frente al beneficio: obliga a mantener las distancias de todos los docentes por cuadro, y ante una oscilación real el promedio puede quedar en un empate que no resuelve nada. Contar coincidencias es más simple de explicar y de auditar.

### Que confirme el operador con un clic

Máxima certeza. Se descarta porque **convierte el pase automático en uno manual**: con veinte docentes entrando, el operador termina haciendo veinte confirmaciones, y ahí ya no hay ninguna ventaja sobre la planilla en papel que el sistema vino a reemplazar.

## Consecuencias

### Positivas

- Una oscilación por iluminación ya no puede escribir una marca equivocada.
- El operador ve que el sistema está trabajando, en vez de un reconocimiento que aparece y desaparece.
- Los tres parámetros son configurables: si en una instalación tres segundos resultan largos o cortos, se ajusta sin tocar código.

### Negativas y limitaciones

- **El pase tarda tres segundos más por persona.** Es el costo directo de la decisión. Con una fila de docentes entrando a la misma hora, se nota.
- Alguien que pase caminando frente a la cámara sin detenerse **no queda marcado**. Es intencional —no había intención de marcar— pero hay que explicarlo, porque de lo contrario se lee como una falla.
- **No corrige la causa.** El reconocimiento sigue siendo sensible a la iluminación; lo que se agrega es una red que evita que esa sensibilidad se convierta en un dato falso. La solución de fondo sigue siendo la migración a embeddings documentada en [ADR-0007](./0007-reconocimiento-facial-lbph.md).
- Si dos personas muy parecidas se turnan frente a la cámara sin pausa, **ninguna de las dos queda marcada**. Es el comportamiento correcto —mejor ninguna marca que una equivocada— pero requiere que alguien lo note y use la carga manual.

## Referencias

- [ADR-0007: Reconocimiento facial con JavaCV + LBPH](./0007-reconocimiento-facial-lbph.md) — la sensibilidad a la iluminación como límite conocido.
- [ADR-0012: Captura guiada del rostro](./0012-captura-guiada-del-rostro.md) — mismo criterio de repartir decisiones entre cliente y servidor.
- `VentanaConfirmacionService` y `ConfirmacionIdentidad` — la lógica y el estado de la racha.
- `VentanaConfirmacionServiceTest` — incluye el caso de dos personas alternándose.
- [Protocolo de calibración](../../7-informes/calibracion-umbral.md) — cómo medir el margen entre candidatos.
