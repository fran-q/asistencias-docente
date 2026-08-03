# Preguntas probables en la defensa

> Cada pregunta trae **la respuesta corta**, **dónde está en el código** y, cuando
> corresponde, **qué no conviene decir**. Las marcadas 🔴 son las que hay que tener
> respondidas sí o sí.

---

## 1. Reconocimiento facial

### 🔴 "El algoritmo que usás, ¿cómo sabe que alguien NO está registrado?"

**Es la mejor pregunta que te pueden hacer, y está resuelta.**

LBPH es un clasificador por vecino más cercano: `predict()` **siempre** devuelve el modelo
más parecido, aunque el parecido sea pésimo. No existe la respuesta "no conozco a esta
persona". Si el sistema se apoyara solo en eso, cualquier rostro sin registrar terminaría
adjudicado a alguien.

Por eso la aceptación exige **dos condiciones**:

1. La distancia del mejor candidato tiene que estar bajo el umbral.
2. Tiene que ganarle al segundo por un margen mínimo.

Un empate no es una identificación: cuál gana lo decide el ruido de la iluminación, no la
cara.

📍 `IdentificacionFacialService.decidir()` — está aislado del resto justamente para poder
probar la regla con casos concretos sin levantar OpenCV.

### 🔴 "¿De dónde salen el umbral de 65 y el margen de 12?"

De medir. Se registraron las distancias de 85 intentos reales y se vio que el umbral
original de 100 aceptaba el 98% de los casos, con márgenes que bajaban a 0,3 — o sea, no
rechazaba prácticamente nada.

**Lo que no conviene ocultar:** la muestra es de tres personas. La regla es correcta y
está fundamentada; los números concretos merecen una calibración con más docentes. Decirlo
antes de que te lo pregunten es mucho mejor que que lo descubran.

📍 `application.properties` → `app.biometria.umbral-confianza`, `margen-minimo`.
📍 ADR-0014 documenta la decisión y los datos.

### 🔴 "Poné una foto del docente en el celular frente a la cámara."

**El sistema la acepta.** No distingue una persona presente de su imagen.

Está documentado como limitación técnica, no escondido. El control que la compensa es
operativo: el pase lo maneja alguien que está mirando la cámara, así que el ataque no es
anónimo.

**Lo que no conviene decir:** que "no da tiempo" o que "es una mejora futura" sin más.
Conviene explicar por qué es difícil: distinguir presencia de imagen requiere o hardware
que el proyecto no tiene (cámara con profundidad, infrarrojo) o modelos entrenados que
exceden el alcance del hito.

### "¿Guardás fotos de los docentes?"

**No.** Las imágenes se procesan en memoria y se descartan. Lo que se guarda es un modelo
matemático LBPH, comprimido y cifrado con AES-GCM, del que no se puede reconstruir la cara.

📍 `ModeloFacialService.registrar()` y `CifradoBiometricoService`.

### "¿Y si el docente se corta el pelo o se pone anteojos?"

Por eso el registro es una **secuencia guiada de cinco poses**, no un video de un minuto.
Veinte fotogramas de la misma pose entrenan un modelo que después no tolera ninguna
variación. El sistema exige además que las capturas sean **distintas entre sí**: si salen
casi idénticas, las rechaza y lo dice.

Si aun así deja de reconocerlo, se re-registra el rostro, que es una operación de un minuto.

📍 ADR-0012 y `CalidadCapturaService.esNovedoso()`.

### "¿Qué pasa si hay dos personas frente a la cámara?"

El sistema avisa cuántas hay y **no dibuja ningún recuadro**. Elegir a una por tamaño
marcaría a la que está más cerca, que no tiene por qué ser la correcta.

📍 `DeteccionRostroService.Extraccion`.

### "¿Se puede marcar con un solo fotograma afortunado?"

No. La misma identidad tiene que sostenerse durante una ventana de 3 segundos. Bajo
iluminación cambiante el reconocimiento oscila entre personas parecidas, y sin esta
condición el primer fotograma que resultara ganador escribiría el registro.

📍 `VentanaConfirmacionService`, ADR-0013.

---

## 2. Marco legal

### 🔴 "Los datos biométricos, ¿son datos sensibles?"

Sí. La Ley 25.326 los define como datos personales, y la **Resolución AAIP 255/2022** los
trata como **sensibles**. Eso implica: consentimiento libre, expreso e informado; base
legal para tratarlos; medidas de seguridad reforzadas; y derechos ARCO garantizados.

### 🔴 "Mostrame el consentimiento y qué pasa si lo revocan."

Hay una pantalla de otorgamiento que registra versión de los términos, método y quién lo
cargó. **Sin consentimiento ACTIVO el sistema no deja registrar el rostro** — no es un
aviso, es un bloqueo.

Al revocarlo, deja de usarse el reconocimiento facial para esa persona y su asistencia
pasa a cargarse manualmente.

📍 `ConsentimientoBiometricoService`, `ModeloFacialService.registrar()` (la validación
está al principio, antes de tocar nada).

### 🔴 "Un docente ejerce derecho de supresión. Mostrame el flujo."

Hay una pantalla por docente que reúne los cuatro derechos: **Acceso** (qué datos tiene el
sistema), **Rectificación** (editar), **Cancelación** (suprimir el biométrico) y
**Oposición** (revocar el consentimiento). Emite además una **constancia en PDF** con lo
que la institución trata sobre esa persona.

La supresión es un **borrado físico**, no una baja lógica: una fila marcada como inactiva
seguiría conteniendo el dato. Y el modelo se saca del caché en memoria **antes** de
borrarlo de la base, porque si no una copia seguiría reconociendo hasta el próximo
reinicio.

Las asistencias ya registradas se conservan como registro administrativo, sin ninguna
referencia biométrica (la clave foránea es `ON DELETE SET NULL`).

📍 `/docentes/{id}/arco`, `ModeloFacialService.suprimirDatosBiometricos()`.

### "¿Cuánto tiempo queda el dato descifrado en memoria?"

Mientras se usa, y hasta 30 minutos después. Hay una tarea programada que descarta los
modelos inactivos. Que estén en memoria mientras el pase corre es el precio de que ande
rápido; que se queden toda la noche no compra nada.

📍 `IdentificacionFacialService.descartarModelosInactivos()`.

---

## 3. Arquitectura

### 🔴 "Es multi-tenant. Convenceme de que A no ve los datos de B."

**Tres capas de defensa:**

1. `TenantInterceptor` publica la institución del usuario antes de que el pedido llegue al
   controlador.
2. `TenantFilterAspect` activa un filtro de Hibernate que agrega la condición a todas las
   consultas, sin que cada repositorio tenga que acordarse.
3. Las consultas críticas reciben el `tenantId` explícito como parámetro.

**Por qué un aspecto y no repetir el `WHERE`:** repetirlo en quince repositorios son
quince oportunidades de olvidarlo, y el costo de olvidarlo es una fuga entre
instituciones. El aspecto se aplica una sola vez y no se puede olvidar.

Hay un test de integración que lo verifica: `AislamientoMultiTenantIT`.

### "¿Qué pasa si dos cámaras marcan al mismo docente en el mismo segundo?"

Se guarda una sola marca. La garantía **no** está en la aplicación sino en la base:
`UNIQUE (docente_id, horario_id, fecha)`. Si dos peticiones compiten, la segunda rebota
contra esa restricción, el sistema la relee y la informa como "ya estaba marcada", sin
error para el operador.

**Por qué en la base y no en Java:** un `if` que consulta y después inserta tiene una
ventana entre las dos operaciones. La base no la tiene.

### "¿Por qué monolito y no microservicios?"

Un equipo, un despliegue, una base. Los microservicios se justifican cuando distintas
partes necesitan escalar o desplegarse por separado, y acá ninguna lo necesita. Lo que sí
se hizo es modularizar por dominio dentro del monolito, para poder separarlo si alguna vez
hiciera falta.

### "¿Por qué Spring Boot?"

Porque el grueso de la aplicación —servidor HTTP, sesiones, transacciones, mapeo a la
base, correo— es infraestructura resuelta hace veinte años. Reescribirla no habría
enseñado nada sobre el problema real.

📍 Ver `docs/4-arquitectura/spring-boot-en-este-proyecto.md`.

### "Los repositorios son interfaces vacías. ¿Cómo funcionan?"

Spring Data genera la implementación en tiempo de ejecución leyendo el **nombre del
método**: `findByDni` se traduce a `SELECT * FROM docentes WHERE dni = ?`. Cuando el
nombre no alcanza para expresar la consulta, se escribe con `@Query`.

### "¿Dónde está la lógica de negocio?"

En `service/`. Los controladores traducen HTTP y las entidades guardan datos. Las
decisiones —Presente vs. Tarde, aceptar o rechazar un rostro, si un año entra en una
carrera— están todas en la capa de servicios.

---

## 4. Base de datos

### "¿Por qué el año está en la materia y no en la comisión?"

Porque es una propiedad del plan de estudios: que Análisis I sea de primero no depende de
si se dicta a la mañana o a la noche. En la comisión habría que repetirlo en cada una, y
dos comisiones de la misma materia podrían declarar años distintos — un estado imposible.

### "¿Por qué la duración vive en la carrera?"

Para que el año de la materia tenga contra qué validarse. Sin ella sería un entero suelto
y nada impediría cargar una materia de quinto en una tecnicatura de tres años.

**Por qué no es un `CHECK` en la base:** haría falta una subconsulta a `carreras`, y
MariaDB no las admite dentro de `CHECK`. La regla vive en `MateriaService`.

### "Las migraciones, ¿se pueden editar?"

No. Flyway guarda un checksum de cada una; si se modifica una ya aplicada, la aplicación
no arranca. Cada cambio es una migración nueva. Van V001 a V011.

### "¿Por qué las bajas son lógicas y la supresión biométrica es física?"

Porque son cosas distintas. Una baja administrativa tiene que poder consultarse después:
"¿este docente estuvo en funciones en marzo?". Una supresión ARCO es lo contrario — el
titular pidió que el dato deje de existir, y una fila inactiva seguiría conteniéndolo.

---

## 5. Preguntas incómodas

### "¿Cuánto de este código escribiste vos?"

Es un trabajo de defensa individual. Lo que conviene tener listo no es una cifra sino
**las decisiones**: por qué dos condiciones y no una para aceptar un rostro, por qué el
aislamiento va en un aspecto, por qué la idempotencia vive en la base. Eso es lo que no se
copia.

### "¿Qué salió mal durante el desarrollo?"

Tener un caso concreto vale más que decir "todo bien". El mejor de este proyecto:

> El sistema identificó a dos personas distintas como el mismo docente. Se revisaron los
> 85 intentos registrados y se vio que aceptaba el 98%, con márgenes de hasta 0,3. La
> causa no era el umbral mal puesto sino una suposición equivocada: creer que el algoritmo
> podía decir "no conozco a esta persona", cuando siempre devuelve al más parecido. La
> corrección fue agregar la segunda condición.

### "¿Cómo sabés que los tests sirven?"

Porque cada test de una regla crítica se verificó **reintroduciendo el bug a propósito** y
comprobando que fallara. Un test que pasa siempre, incluso con el código roto, no prueba
nada.

### "¿Qué te falta?"

- Distinguir a la persona de su fotografía.
- Calibrar el umbral con más docentes.
- Gráficos en los reportes y exportación nativa a `.xlsx`.

Está todo declarado en la matriz de alcance con su estado.

---

## 6. Lo que conviene tener abierto en la defensa

| Para | Archivo |
|---|---|
| La regla del reconocimiento | `IdentificacionFacialService.decidir()` |
| Los tres controles multi-tenant | `TenantInterceptor`, `TenantFilterAspect` |
| El bloqueo por consentimiento | `ModeloFacialService.registrar()` (primeras líneas) |
| La supresión ARCO | `ModeloFacialService.suprimirDatosBiometricos()` |
| La idempotencia | `V001__init.sql`, `UNIQUE` de asistencias |
| Las decisiones tomadas | `docs/4-arquitectura/adr/` |
