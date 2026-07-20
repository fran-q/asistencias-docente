# Protocolo de calibración del umbral de confianza (RF-16) y medición de tiempos (RNF-01)

**Objetivo:** reemplazar el valor por defecto `app.biometria.umbral-confianza=100.0`
por un valor **calibrado con datos reales**, y obtener los tiempos medidos que
piden las preguntas 4 y 18 del dossier de defensa.

## Por qué se calibra así (el concepto)

LBPH devuelve una **distancia**: menor = más parecido. El umbral es la línea
que separa "es la misma persona" de "no lo es". El criterio rector del proyecto
es **minimizar falsos positivos**: es preferible no reconocer a un docente
presente (que termina en carga manual, RF-22) antes que confundir a una persona
con otra (que mete un dato falso en un registro legal).

En la práctica eso significa: el umbral se elige **por debajo de la distancia
mínima que produce un impostor**, con margen. Los falsos negativos que eso
genere los absorbe la carga manual — está diseñado para eso.

## Instrumentación disponible

Cada intento de identificación con rostro detectado escribe en el log de la app
una línea así:

```
CALIBRACION reconocido=true docenteId=2 distancia=62.3 umbral=100.0 modelosComparados=2 msDeteccion=45 msComparacion=12 msTotal=57
```

Y cada marca de asistencia creada escribe:

```
RNF01 pase completo: docente=2 estado=PRESENTE yaEstaba=false msTotal=61
```

- `distancia` — el dato de calibración del umbral.
- `msTotal` de `CALIBRACION` — detección + comparación (núcleo del RNF-01).
- `msTotal` de `RNF01` — el ciclo completo incluyendo el registro en BD.

> **Ojo con el cache frío:** la PRIMERA identificación tras arrancar la app
> incluye descifrar y deserializar todos los modelos. Descartá esa medición;
> los tiempos reales son los de las llamadas siguientes (cache caliente).

## Protocolo (una sesión de ~1 hora)

### Preparación
1. `./gradlew bootRun` con al menos 1 docente con rostro registrado.
2. Abrir el **Pase de asistencia**, encender cámara, iniciar pase.
3. Tener a mano el log de la consola (las líneas `CALIBRACION`).

### Serie A — Rostros genuinos (deberían reconocerse)
Con el docente registrado frente a la cámara, tomar **20 lecturas** variando:
- distancia a la cámara (cerca / lejos),
- ángulo (frente / leve perfil),
- iluminación (luz normal / contraluz / luz lateral),
- accesorios (con y sin lentes si aplica).

Anotar la columna `distancia` de cada línea `CALIBRACION`.

### Serie B — Impostores (NO deberían reconocerse)
Con **2 o 3 personas NO registradas** frente a la cámara, tomar **20 lecturas**
en condiciones similares. Anotar las distancias.

### Planilla

| # | Serie | Distancia | ¿Reconocido con umbral actual? | Nota (luz/ángulo) |
|---|---|---|---|---|
| 1 | A (genuino) | | | |
| … | | | | |
| 21 | B (impostor) | | | |
| … | | | | |

### Análisis y decisión

1. **Distancia máxima genuina** (el peor caso en que sí sos vos): `D_gen_max`.
2. **Distancia mínima impostora** (el impostor que más se acercó): `D_imp_min`.
3. **Caso sano** (`D_imp_min` claramente mayor que `D_gen_max`): elegir el
   umbral entre ambos, más cerca del lado genuino:
   `umbral = D_gen_max + (D_imp_min - D_gen_max) * 0.25`
4. **Caso solapado** (algún impostor por debajo de algún genuino): priorizar
   los falsos positivos → `umbral = D_imp_min * 0.9`, aceptando que algunas
   lecturas genuinas fallen (van a carga manual). Anotar cuántas.

Actualizar en `application.properties`:
```properties
app.biometria.umbral-confianza=<valor elegido>
```

### Métricas para el dossier

Con las 40 lecturas, completar:

| Métrica | Valor |
|---|---|
| Intentos genuinos / reconocidos (con el umbral final) | __ / 20 |
| Intentos impostores / rechazados (con el umbral final) | __ / 20 |
| **Falsos positivos** | __ (objetivo: 0) |
| **Falsos negativos** | __ (aceptables: los absorbe la carga manual) |
| Mediana de `msTotal` (CALIBRACION, cache caliente) | __ ms |
| Máximo de `msTotal` (RNF01, ciclo completo) | __ ms |
| ¿Cumple el presupuesto de 3 s del RNF-01? | Sí / No |

### Dónde volcar los resultados

- `application.properties` → el nuevo umbral.
- Dossier de defensa → sección 4.2 (valor + método) y preguntas 4 y 18 del
  banco de preguntas.
- Si el umbral cambió respecto de 100.0, dejar una línea en el ADR-0007
  (sección de calibración) con el valor, la fecha y el tamaño de la muestra.

## Limitación conocida (para decir en la defensa)

Una calibración con 1-3 personas es una **muestra chica**: valida el método,
no generaliza a 400 docentes. La respuesta madura en la defensa es: *"calibré
con N personas siguiendo un protocolo documentado; en un despliegue real la
calibración se repetiría con una muestra representativa de la institución, y
el criterio de minimizar falsos positivos se mantiene"*.
