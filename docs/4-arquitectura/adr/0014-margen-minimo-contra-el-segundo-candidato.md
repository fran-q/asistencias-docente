# ADR-0014: Un reconocimiento necesita distancia baja Y margen sobre el segundo

**Estado**: Aceptada
**Fecha**: 2026-08-01
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Probando el pase con varias personas apareció un falso positivo directo: **dos personas visiblemente distintas fueron identificadas como el mismo docente**, con su nombre en pantalla y el recuadro en amarillo como si el reconocimiento hubiera sido correcto.

No era un caso límite ni una coincidencia rara. El log de calibración, sobre 85 intentos acumulados, mostraba esto:

| Métrica | Valor |
|---|---|
| Intentos | 85 |
| **Aceptados** | **83 (98 %)** |
| Rechazados | 2 |
| Distancia (mín / mediana / máx) | 28,9 / 50,2 / 108,8 |
| Margen contra el segundo (mín / mediana / máx) | 0,3 / 14,4 / 31,7 |
| Intentos con margen menor a 5 | 15 de 80 |

El sistema aceptaba prácticamente todo lo que se le pusiera delante.

### La causa

**LBPH no sabe decir "no conozco a esta persona".** Su `predict()` compara el rostro contra cada modelo cargado y devuelve el más parecido, junto con una distancia. No existe una respuesta "ninguno de estos": siempre hay un ganador, aunque el parecido sea pésimo.

Es un clasificador de vecino más cercano, y un vecino más cercano siempre encuentra un vecino.

La única cosa que convertía eso en un "no reconocido" era el umbral de confianza, y **estaba en 100** mientras las distancias reales de esta instalación caían entre 28 y 108. Con ese valor casi nada quedaba afuera.

El segundo síntoma estaba en la columna del margen: en 15 de 80 intentos el mejor y el segundo candidato quedaron separados por menos de 5 puntos. En esos casos cuál "ganaba" lo decidía el ruido —una sombra, un cambio de luz— y no la identidad de la persona.

Ese es además el mismo fenómeno que motivó la ventana de confirmación de [ADR-0013](./0013-ventana-de-confirmacion-del-pase.md). Pero la ventana solo protege contra la oscilación: **no ayuda cuando el candidato equivocado gana de forma consistente**, que es exactamente lo que pasó acá.

## Decisiones

### 1. Un reconocimiento exige dos condiciones, no una

```
        distancia <= umbral        Y        (segunda - mejor) >= margen
        └── ¿se le parece?                  └── ¿le gana claramente al otro?
```

Si falla la primera, la respuesta es **"rostro no registrado"**. Si falla la segunda, es **"no se pudo distinguir entre dos rostros parecidos"**.

Son dos problemas distintos y se resuelven distinto: el primero registrando el rostro, el segundo acercándose a la cámara o mejorando la luz. Devolver el mismo mensaje para ambos obligaba a adivinar.

### 2. El margen es la condición que faltaba

Un empate no es una identificación. Si dos modelos quedan a un punto de distancia, el sistema no distinguió a nadie: eligió por ruido.

Esta condición se había mencionado en ADR-0013 como "complemento disponible" y se descartó entonces por no ser suficiente **por sí sola**. Lo sigue sin ser: lo que este ADR agrega es que tampoco el umbral alcanza solo. Hacen falta las dos.

### 3. Con un solo modelo cargado el margen no aplica, y eso es una debilidad

Si la institución tiene un único rostro registrado no hay segundo candidato contra quien medir, así que la única defensa es el umbral.

Conviene decirlo con todas las letras: **es el punto más débil del sistema**. Cualquier persona que se parezca lo suficiente al único modelo entra. Al agregar modelos la situación mejora, porque un desconocido tiende a repartir su parecido entre varios y el margen se achica.

### 4. El umbral se calibra midiendo, no ajustando hasta que "ande"

El valor por defecto pasó de 100 a **65**, y el margen se fijó en **12**. Salen de las 85 mediciones citadas arriba: los aciertos caían entre 28 y 55, y las confusiones entre 73 y 90.

**Estos números dependen de la cámara y de la iluminación**, así que en otra instalación hay que repetir la medición. El procedimiento quedó documentado en el Manual Técnico, sección 7.2.

## Alternativas descartadas

### Solo bajar el umbral

Es la reacción inmediata y es insuficiente. Bajarlo lo bastante como para excluir todas las confusiones observadas (que llegaban a 73) empezaría a rechazar aciertos legítimos, porque los rangos se rozan. El margen separa casos que el umbral no puede separar: dos personas parecidas producen **ambas** distancias bajas, y ninguna elección de umbral las distingue.

### Confiar en la ventana de confirmación de ADR-0013

Ya estaba implementada y no evitó este fallo. La ventana exige que la identidad se **sostenga**, lo cual protege contra la oscilación entre dos candidatos. Pero cuando el candidato equivocado gana consistentemente —que es lo que ocurre con un umbral demasiado permisivo— la ventana se cumple sin problema y la marca se escribe igual.

Las dos defensas son complementarias y cubren fallos distintos.

### Migrar a embeddings ahora

Un descriptor moderno (SFace, ArcFace) daría separación mucho mayor y una noción de umbral más estable. Sigue siendo la salida de fondo, documentada en [ADR-0007](./0007-reconocimiento-facial-lbph.md). Se descarta **para este momento** porque implica traer un modelo ONNX y volver a registrar todos los rostros, y porque el problema inmediato —aceptar al 98 % de quien se presente— se corrige sin eso.

## Consecuencias

### Positivas

- Una persona sin registrar deja de ser identificada como un docente.
- El operador ve **por qué** se rechazó, no solo que se rechazó.
- Las dos condiciones son configurables y calibrables con datos que el sistema ya venía registrando.
- Los casos límite quedaron fijados en pruebas con las distancias reales del incidente.

### Negativas y limitaciones

- **Va a rechazar más.** Con el umbral en 65, un docente registrado bajo iluminación distinta a la del registro puede quedar afuera. La salida es volver a registrarle el rostro con la captura guiada, no subir el umbral.
- Dos personas muy parecidas —hermanos, un caso real acá— pueden quedar en un rechazo permanente por margen. El sistema prefiere no marcar antes que marcar mal, pero para esas personas la carga manual pasa a ser la vía normal.
- **Con un solo modelo registrado la protección del margen no existe** (decisión 3).
- Los valores por defecto son de **esta** instalación. Publicarlos como si fueran universales sería un error.

## Referencias

- [ADR-0007: Reconocimiento facial con JavaCV + LBPH](./0007-reconocimiento-facial-lbph.md) — los límites del algoritmo y la migración pendiente.
- [ADR-0013: Ventana de confirmación](./0013-ventana-de-confirmacion-del-pase.md) — la defensa complementaria, y por qué no alcanzaba.
- `IdentificacionFacialService.decidir` — la regla, aislada para poder probarla.
- `IdentificacionFacialServiceTest` — casos construidos con las distancias reales del incidente.
- Manual Técnico, sección 7.2 — cómo recalibrar midiendo.
