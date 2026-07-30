# Protocolo de calibración del umbral de confianza (RF-16) y medición de tiempos (RNF-01)

**Objetivo:** reemplazar el valor por defecto `app.biometria.umbral-confianza=100.0`
por un valor **calibrado con datos reales**, comprobar que **dos docentes registrados
no se confundan entre sí**, y obtener los tiempos medidos que piden las preguntas 4 y
18 del dossier de defensa.

## Por qué se calibra así (el concepto)

LBPH devuelve una **distancia**: menor = más parecido. El umbral es la línea
que separa "es la misma persona" de "no lo es". El criterio rector del proyecto
es **minimizar falsos positivos**: es preferible no reconocer a un docente
presente (que termina en carga manual, RF-22) antes que confundir a una persona
con otra (que mete un dato falso en un registro legal).

En la práctica eso significa: el umbral se elige **por debajo de la distancia
mínima que produce un impostor**, con margen. Los falsos negativos que eso
genere los absorbe la carga manual — está diseñado para eso.

### Los dos errores no valen lo mismo

| Error | Qué pasa | Gravedad |
|---|---|---|
| **Falso positivo** | Reconoce a Juan como María | **Grave**: dato falso en un registro legal, y afecta a dos personas reales |
| **Falso negativo** | No reconoce a Juan, que sí está | Molesto: no registra y deriva a carga manual (RF-22 a RF-24) |

### Por qué no alcanza con mirar si acertó

Que el sistema acierte no significa que esté funcionando bien. Si Juan da distancia 45
contra su propio modelo y 48 contra el de María, **acertó por 3 puntos**: un fotograma
con peor luz y se equivoca. Esa diferencia se llama **margen**, y es lo que distingue un
sistema que separa personas de uno que tuvo suerte.

Por eso el protocolo mide el margen además de la distancia.

## Instrumentación disponible

Cada intento de identificación con rostro detectado escribe en el log de la app
una línea así:

```
CALIBRACION reconocido=true mejorDocente=1 mejorDistancia=42.3 segundoDocente=2 segundaDistancia=88.7 margen=46.4 umbral=100.0 modelosComparados=3 msDeteccion=45 msComparacion=12 msTotal=57
```

Y cada marca de asistencia creada escribe:

```
RNF01 pase completo: docente=2 estado=PRESENTE yaEstaba=false msTotal=61
```

| Campo | Para qué sirve |
|---|---|
| `mejorDocente` | **Quién cree el sistema que es.** Se registra siempre, aunque lo rechace |
| `mejorDistancia` | El dato para calibrar el umbral |
| `segundoDocente` / `segundaDistancia` | Contra quién estuvo cerca de confundirse |
| `margen` | `segundaDistancia - mejorDistancia`. **La métrica que dice si separa bien** |
| `msTotal` de `CALIBRACION` | Detección + comparación (núcleo del RNF-01) |
| `msTotal` de `RNF01` | El ciclo completo incluyendo el registro en BD |

> **Ojo con el cache frío:** la PRIMERA identificación tras arrancar la app
> incluye descifrar y deserializar todos los modelos. Descartá esa medición;
> los tiempos reales son los de las llamadas siguientes (cache caliente).

## Protocolo (una sesión de ~1 hora)

### Preparación

1. **Registrar al menos 3 docentes** con rostro. Con uno solo no se puede medir el
   margen: hace falta un segundo modelo contra el cual comparar.
2. Si se puede, elegir personas **parecidas entre sí** (mismo sexo, edad similar,
   todas con lentes o ninguna). Suena contraintuitivo, pero se calibra para el peor
   caso: si separa bien a dos personas parecidas, con el resto sobra.
3. `./gradlew bootRun` y abrir el **Pase de asistencia**.
4. Tener a mano el log de la consola (las líneas `CALIBRACION`).

En las tres series, **variar las condiciones**: distancia a la cámara, ángulo (frente
y leve perfil), iluminación (normal, contraluz, lateral) y accesorios. Medir todo en
condiciones ideales produce un umbral que falla el día de la demostración.

### Serie A — Genuinos (deberían reconocerse)

Cada docente registrado frente a la cámara, contra su propio modelo. **20 lecturas**
repartidas entre los tres. Anotar `mejorDistancia`.

### Serie B — Impostores externos (NO deberían reconocerse)

**2 o 3 personas no registradas** frente a la cámara. **20 lecturas**. Anotar
`mejorDistancia`: es la distancia del desconocido al modelo que más se le parece.

### Serie C — Cruce entre registrados (el más importante)

Esta serie responde la pregunta que de verdad importa: **¿puede el sistema confundir a
dos docentes registrados entre sí?**

Es el error más grave del sistema, peor que dejar entrar a un desconocido: si Juan es
reconocido como María, **María queda con una asistencia falsa y Juan pierde la suya**.
Dos registros corrompidos, y ambos de personas reales con consecuencias laborales.

Cada docente registrado se para frente a la cámara y se observan **dos cosas** en el log:

1. **`mejorDocente` tiene que ser quien realmente está.** Si alguna vez no lo es, hay un
   falso positivo de cruce. **Con que ocurra una sola vez en 20, el umbral no alcanza** y
   hay que atacar el problema de fondo (ver más abajo).
2. **El `margen`**, aunque haya acertado.

**20 lecturas.** Anotar `mejorDocente`, quién estaba realmente, y el `margen`.

### Cómo leer el margen

| Margen | Interpretación |
|---|---|
| **> 30** | Separación cómoda. El sistema distingue bien a esas personas |
| **10 a 30** | Zona de riesgo: anda, pero un cambio de luz puede darlo vuelta |
| **< 10** | **Alarma.** Están casi empatados; es cuestión de tiempo que se confundan |

Un acierto con margen de 3 no es un acierto: es suerte.

### Planilla

| # | Serie | Quién estaba | `mejorDocente` | `mejorDistancia` | `margen` | ¿Correcto? | Nota (luz/ángulo) |
|---|---|---|---|---|---|---|---|
| 1 | A | | | | | | |
| … | | | | | | | |
| 21 | B | (no registrado) | | | — | | |
| … | | | | | | | |
| 41 | C | | | | | | |
| … | | | | | | | |

## Análisis y decisión

### 1. El umbral

1. **`D_gen_max`** — la distancia más alta de una lectura genuina (Serie A).
2. **`D_imp_min`** — la distancia más baja de un impostor (Series B **y C**: un cruce
   entre registrados cuenta como impostor).
3. **Caso sano** (`D_imp_min` claramente mayor que `D_gen_max`): elegir el umbral entre
   ambos, más cerca del lado genuino:

   ```
   umbral = D_gen_max + (D_imp_min - D_gen_max) * 0.25
   ```

   El `0.25` es deliberado: deja el 75 % del colchón como margen de seguridad contra
   falsos positivos.

4. **Caso solapado** (algún impostor por debajo de algún genuino): priorizar los falsos
   positivos → `umbral = D_imp_min * 0.9`, aceptando que algunas lecturas genuinas
   fallen y vayan a carga manual. **Anotar cuántas**: es la tasa de falsos negativos.

Actualizar en `application.properties`:

```properties
app.biometria.umbral-confianza=<valor elegido>
```

### 2. Qué hacer si la Serie C falla

Si hubo cruces o los márgenes son chicos, **no bajar el umbral para tapar el problema**:
eso solo cambia falsos positivos por falsos negativos. Atacar las causas, en este orden:

1. **Volver a registrar el rostro con la captura guiada.** Desde ADR-0012 el registro
   pide cinco poses y verifica que las capturas sean distintas entre sí, justamente
   porque un modelo entrenado con una sola pose repetida es frágil ante cualquier
   variación. Un docente registrado con el flujo viejo —treinta segundos quieto de
   frente— tiene un modelo pobre: **volver a registrarlo es lo primero a probar**.
2. **Iluminación.** LBPH es sensible a la luz — es su debilidad conocida. Luz frontal
   difusa, nunca contraluz. Es gratis y suele ser lo que más mejora.
3. **Si aun así no separa, es el límite del algoritmo.** LBPH compara patrones de
   textura, no entiende de rostros. La respuesta correcta no es ajustar el umbral sino
   la **migración a embeddings** documentada en el ADR-0007, con el camino ya verificado:
   `FaceRecognizerSF` (SFace) está en el classpath y solo falta el modelo ONNX.

## Métricas para el dossier

Con las 60 lecturas, completar:

| Métrica | Valor |
|---|---|
| Umbral elegido y criterio aplicado (sano / solapado) | __ |
| Intentos genuinos / reconocidos (con el umbral final) | __ / 20 |
| Impostores externos / rechazados | __ / 20 |
| **Cruces entre registrados (Serie C) / correctos** | __ / 20 |
| **Falsos positivos totales** | __ (objetivo: 0) |
| **Falsos negativos** | __ (aceptables: los absorbe la carga manual) |
| **Margen mínimo observado en la Serie C** | __ |
| **Margen mediano en la Serie C** | __ |
| Mediana de `msTotal` (CALIBRACION, cache caliente) | __ ms |
| Máximo de `msTotal` (RNF01, ciclo completo) | __ ms |
| ¿Cumple el presupuesto de 3 s del RNF-01? | Sí / No |

## Dónde volcar los resultados

- `application.properties` → el nuevo umbral.
- Dossier de defensa → sección 4.2 (valor + método) y preguntas 4 y 18 del
  banco de preguntas.
- Si el umbral cambió respecto de 100.0, dejar una línea en el ADR-0007
  (sección de calibración) con el valor, la fecha y el tamaño de la muestra.

## Limitaciones conocidas (para decir en la defensa)

**La muestra es chica.** Una calibración con 3 personas valida el método, no generaliza
a 400 docentes. La respuesta madura es: *"calibré con N personas siguiendo un protocolo
documentado; en un despliegue real la calibración se repetiría con una muestra
representativa de la institución, y el criterio de minimizar falsos positivos se
mantiene"*.

**El margen depende de quiénes estén registrados.** Con 3 docentes el margen va a ser
más generoso que con 400: cuantos más rostros hay en el padrón, más probable es que
alguno se parezca. Es una propiedad del reconocimiento 1:N, no un defecto de esta
implementación, y conviene decirlo antes de que lo pregunten.
