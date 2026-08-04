# Revisión de UI/UX

> Hecha recorriendo la aplicación en el navegador, midiendo sobre el HTML y el CSS
> reales. Lo que está marcado **corregido** ya se aplicó; lo demás queda como
> observación con su fundamento.

---

## 1. Lo que está bien resuelto

**La navegación no te deja perdido.** El grupo del menú donde estás parado queda
subrayado aunque el desplegable esté cerrado, y el logo vuelve al inicio. Son dos cosas
chicas que se notan cuando faltan.

**Las acciones destructivas piden confirmación.** La baja de un docente abre un modal
que además pide la fecha, en vez de asumir "hoy" — la baja se carga días después del
hecho, y forzar hoy falsearía el registro.

**Los mensajes de error dicen qué hacer.** No dicen "año inválido" sino *"La carrera
'Tecnicatura en Economía 2024' dura 3 años, así que no puede tener materias de 5° año"*.
La diferencia es si el administrador tiene que ir a averiguar contra qué se comparó.

**Los formularios reponen lo tipeado cuando el guardado falla.** Verificado: tras un
error, código, nombre y duración vuelven con lo que había, no en blanco.

**El contraste cumple.** Modo oscuro y claro, texto principal 15,1:1 y texto atenuado
5,8:1 sobre el fondo. WCAG AA pide 4,5:1.

---

## 2. Problemas encontrados y corregidos

### 2.1 No había indicador de foco visible 🔴

**El más importante de todos.** Ningún elemento mostraba dónde estaba parado el foco al
navegar con teclado. La causa no era un olvido sino un patrón mal aplicado: los campos
tenían

```css
.form-group input:focus { outline: none; border-color: var(--primary); }
```

es decir, se quitaba el anillo del navegador y se lo reemplazaba por un **borde azul**.
Eso parece equivalente y no lo es: entre un borde gris y uno azul hay muy poco contraste
*entre estados*, y para alguien con daltonismo son casi el mismo. Incumple WCAG 2.4.7.

**Corregido:** el `outline: none` ahora aplica solo a `:focus:not(:focus-visible)` —o
sea, al foco de mouse, donde el anillo es ruido— y el teclado recupera el anillo. El
borde azul queda como refuerzo, no como el indicador.

### 2.2 Para llegar al contenido con teclado había que recorrer todo el menú

Cada pantalla obligaba a tabular por el logo, cuatro grupos de navegación, el usuario y
el botón de salir antes de tocar el primer campo. En cada pantalla.

**Corregido:** enlace *"Saltar al contenido"*, invisible hasta que se lo tabula.

### 2.3 El lector de pantalla anunciaba años que no se podían elegir

El select de año ocultaba las opciones sobrantes con `hidden` + `disabled`. Eso las saca
de la vista pero **no del árbol de accesibilidad**: un lector de pantalla seguía leyendo
*"5° año, 6° año… 10° año"* en una carrera de tres.

**Corregido:** se sacan del DOM y se reponen si se elige una carrera más larga.

### 2.4 La grilla del inicio saltaba de tres columnas a una

Medido a distintos anchos:

| Ancho de pantalla | Antes | Problema |
|---|---|---|
| 1366 px | 3 columnas de 373 px | bien |
| 1100 px | 3 columnas de 340 px | bien |
| **1024 px** | 3 columnas de **315 px** | apretadas |
| **1000 px** | **1 columna de 952 px** | cuatro números chiquitos flotando |

Veinticuatro píxeles separaban "tres columnas apretadas" de "una columna gigante y
vacía". Faltaba el escalón intermedio.

**Corregido:** `repeat(auto-fit, minmax(320px, 1fr))`. La grilla mete tantas columnas de
al menos 320 px como entren y baja sola, sin corte fijo.

### 2.5 Un botón decía una cosa y hacía otra

En la pantalla de derechos ARCO, el botón **"Suprimir"** no suprimía: llevaba a otra
pantalla donde había que volver a pedirlo. Un botón destructivo cuya etiqueta no coincide
con lo que hace es peor que uno mal ubicado.

**Corregido:** ahora dice *"Ir a suprimir"*.

### 2.6 Dos números del tablero contradecían la base 🔴

Encontrado probando: la pantalla de inicio decía **"0 ausentes"** con dos filas AUSENTE
en la base, y **"1 de 1 docentes ya marcaron (100%)"** para un docente que no había
venido.

Los dos salen del mismo error: el código trataba *"tiene fila de asistencia"* como
sinónimo de *"marcó presente"*. Era cierto hasta que existió el generador de ausencias,
que persiste una fila por cada clase que terminó sin marca.

El síntoma es especialmente feo porque el **listado de asistencias sí mostraba las
ausencias**: dos pantallas de la misma app decían cosas distintas sobre el mismo día.

**Corregido**, y comprobado por mutación.

---

## 3. Observaciones que quedan abiertas

### 3.1 El combo con búsqueda no está completo para lectores de pantalla

Funciona con teclado (flechas, Enter, Escape) y filtra bien, pero le faltan
`aria-controls`, `aria-activedescendant` e `id` en cada opción. Un lector de pantalla lo
anuncia como un campo de texto común y no dice cuántas opciones quedaron ni cuál está
resaltada.

**Costo de arreglarlo:** bajo. **Por qué no se hizo ahora:** es una mejora incremental
sobre algo que ya funciona, a diferencia del foco visible, que era una barrera completa.

### 3.2 Ningún listado tiene paginación

Los catálogos —carreras, materias, comisiones, docentes— son naturalmente chicos, y con
el encabezado fijo se recorren bien. El riesgo real está en **asistencias**, que crece
todos los días. El reporte ya tiene tope con aviso; el listado del día no lo necesita
porque está acotado a una fecha.

### 3.3 El pase de asistencia no avisa si la cámara se corta

Si el navegador pierde el dispositivo a mitad del pase —otra aplicación lo toma, se
desconecta el USB— el loop sigue mandando fotogramas vacíos y la pantalla dice "no se
detecta ningún rostro", que es indistinguible de "no hay nadie". El operador puede tardar
minutos en darse cuenta.

---

## 4. Para el día a día de un administrador

### Lo que va a hacer todos los días, y cuánto le cuesta

| Tarea | Clicks | ¿Cómodo? |
|---|---|---|
| Abrir el pase y empezar | 2 (menú → Iniciar pase) | Sí. El botón del inicio lo hace en 1. |
| Ver cómo viene el día | 0 — es la pantalla de inicio | Sí |
| Ver los ausentes de hoy | 1 desde el inicio (el número enlaza al listado filtrado) | Sí |
| Cargar una asistencia manual | 3 | Aceptable |
| Registrar el rostro de un docente | 4 + la secuencia guiada | Aceptable, es esporádico |

### Situaciones que le van a resultar incómodas

**Cargar el plan de estudios de cero.** Para que una clase pueda marcarse hacen falta
cinco altas encadenadas: carrera → materia → comisión → horario → docente, más el
consentimiento y el registro del rostro. Cada una en su pantalla, y si se saltea una, el
sistema no marca. El panel *"Requiere atención"* mitiga bastante —dice exactamente qué
falta y lleva ahí— pero al empezar de cero sigue siendo un recorrido largo. **Un asistente
de puesta en marcha que encadene los pasos sería la mejora de mayor impacto para alguien
que arranca.**

**Docentes con apellidos parecidos.** Hay un *Quiorga, Francisco* y un *Quiroga, Máximo*
en la misma institución. El desplegable los distingue por DNI, y el legajo aparece solo si
está cargado —que hoy no lo está en ninguno. Escribir "quir" trae uno solo, porque el otro
tiene la i y la o cambiadas. **Cargar los legajos evita el error mucho antes que cualquier
cambio de interfaz.**

**Un error de reconocimiento en el momento del pase.** Si el sistema rechaza a alguien que
sí está registrado, el operador no tiene forma de resolverlo desde esa pantalla: tiene que
salir del pase, ir a carga manual, buscar al docente y cargar la asistencia con su motivo.
Con un docente esperando adelante, eso es incómodo. **Un acceso directo a carga manual
desde la propia pantalla del pase ahorraría ese rodeo.**

**El pase no dice qué clase está esperando.** La pantalla muestra la cámara y el
resultado, pero no *"ahora corresponde FIS-103 A, 11:00–12:00"*. El operador no puede
anticipar si el docente que tiene adelante es el que se espera. El dato ya se calcula para
la pantalla de inicio.

**El horario admite tolerancias que no parecen intencionales.** Hay uno cargado con **120
minutos** de tolerancia: la ventana abre dos horas antes de la clase. Es válido según la
validación (0 a 120), pero difícilmente sea lo que alguien quiso. **Una advertencia por
encima de, digamos, 30 minutos evitaría el error de tipeo.**

---

## 5. Resumen

| | |
|---|---|
| Corregido en esta revisión | 6 problemas, 2 de ellos de datos incorrectos en pantalla |
| Observaciones abiertas | 3 técnicas + 5 de flujo diario |
| Mejora de mayor impacto pendiente | Asistente de puesta en marcha |
| Barrera de accesibilidad más grave | Foco visible — **ya corregida** |
