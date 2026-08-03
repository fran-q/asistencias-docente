# ADR-0012: Captura guiada del rostro, por calidad y no por tiempo

**Estado**: Aceptada
**Fecha**: 2026-07-30
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

El registro del modelo facial grababa **30 segundos** y tomaba un cuadro cada 1,5 segundos: unos veinte frames. El servidor descartaba los que no tuvieran exactamente un rostro detectable y entrenaba el LBPH con lo que quedara, exigiendo un mínimo de cinco.

Al intentar cargar los rostros de tres docentes reales, dos de los tres registros fallaron. Eso obligó a mirar el flujo de cerca, y el problema resultó ser más profundo que los fallos.

### El problema no eran los frames descartados

Eran los aceptados. **Una persona quieta frente a la cámara durante treinta segundos produce veinte fotografías casi idénticas.** El LBPH entrenado con eso aprende una sola pose, con una sola distancia a la cámara y una sola inclinación de cabeza. Después, en el pase, basta que el docente incline levemente la cabeza o se pare un paso más lejos para que la distancia se dispare y el reconocimiento falle o quede al borde del umbral.

Dicho de otro modo: el sistema entrenaba veinte veces la misma imagen y esperaba tolerancia a la variación.

### Tres huecos concretos

1. **La duración no garantizaba nada.** Treinta segundos con mala luz producen treinta segundos de frames inútiles.
2. **No se medía la calidad.** Alcanzaba con que el Haar Cascade encontrara una cara. Un cuadro movido o a contraluz, con la cara detectable, entraba al entrenamiento y lo degradaba en silencio.
3. **El mensaje de error no servía.** Decía siempre lo mismo — *"no se detectó tu cara de forma estable"* — sin distinguir si faltó luz, si estaba borroso o si no se detectó nada. Quien fallaba tenía que repetir a ciegas.

## Decisiones

### 1. La captura se guía por etapas y termina por calidad, no por reloj

Se le piden cinco poses, tres capturas de cada una. La pantalla muestra la instrucción, y la cámara **captura sola** cuando la imagen cumple los criterios durante dos lecturas seguidas.

Si por la luz alguien tarda el doble, tarda el doble y sale un modelo bueno. Antes, tardara lo que tardara, salía lo que saliera.

Las dos lecturas seguidas evitan pescar el instante en que la persona pasa por la pose de camino a otra cosa.

### 2. Los giros son suaves, y eso no es una simplificación sino un requisito

Las etapas piden girar **apenas** la cabeza, no ponerse de perfil.

El motivo es que el clasificador que entrena y reconoce es de **rostro frontal**. Ante un perfil marcado no encuentra la cara —así que el frame se descarta y la etapa nunca se completa— o, peor, encuentra algo mal alineado. El LBPH compara texturas en posiciones fijas del recorte: un recorte desalineado no aporta tolerancia, agrega ruido.

Pedir un perfil completo habría empeorado el modelo mientras daba la sensación de estar mejorándolo.

### 3. No se verifica el ángulo de la cabeza, y se dice

El Haar Cascade devuelve un rectángulo, no una orientación. **No hay forma de comprobar que la persona giró a la izquierda cuando se le pidió.**

En vez de fingir esa verificación, se separa lo que se induce de lo que se comprueba:

| Instrucción | Cómo se trata |
|---|---|
| Acercate / Alejate | **Se verifica**: área del recuadro sobre el área del cuadro |
| Quedate quieto | **Se verifica**: varianza del Laplaciano |
| Hay poca luz / estás a contraluz | **Se verifica**: brillo medio y desvío estándar del recorte |
| Girá apenas a la izquierda | **Se induce**, no se verifica |

Lo que sí se comprueba es la consecuencia: que las capturas resulten **distintas entre sí**. Es la garantía real, y no depende de creerle a nadie.

### 4. Las capturas tienen que ser distintas entre sí

Antes de entrenar, cada recorte normalizado se compara con los ya aceptados mediante la diferencia media absoluta. Si es menor al umbral, se descarta por repetida.

**Sin esta comprobación, la secuencia guiada no garantizaría nada más que la grabación anterior**: cinco etapas hechas sin moverse producirían las mismas quince fotos iguales. Es la pieza que convierte a la guía en algo más que una decoración.

### 5. La calidad se mide sobre el gris SIN ecualizar

El pipeline aplica `equalizeHist` antes de detectar, porque le mejora el contraste al clasificador. Pero **la calidad se mide sobre el gris original**.

Ecualizar aplana el histograma: una cara a oscuras y una bien iluminada quedan parecidas después de ecualizar, así que medir el brillo sobre la imagen ecualizada no distinguiría nada. El recorte que entrena sí sale del ecualizado, que es lo que estabiliza al LBPH frente a los cambios de iluminación entre el registro y el pase.

### 6. El navegador decide cuándo capturar; el servidor decide si sirve

Toda evaluación es una llamada a `/reconocimiento/detectar`, que corre **el mismo Haar Cascade** que después entrena y reconoce.

Se podría evaluar en el navegador y sería más rápido, pero con otro detector: el recuadro diría "perfecto" y el entrenamiento después descartaría ese mismo frame. El feedback tiene que venir del motor que toma la decisión final, o miente.

Y al enviar, **el servidor revalida todo**, aunque el cliente ya haya filtrado con los mismos criterios. El cliente elige el momento; no dictamina si la captura sirve. Es el mismo criterio de desconfianza que el resto del sistema aplica a los datos que llegan del navegador.

### 7. El mensaje de error dice qué corregir

Al fallar se cuenta qué tipo de descarte dominó —sin rostro, mala calidad o repetidas— y se responde en consecuencia: buscar mejor luz, moverse de verdad entre etapas, o mirar de frente sin gorra.

## Alternativas descartadas

### Detectar la orientación con cascadas adicionales (`haarcascade_profileface`)

OpenCV incluye un clasificador de perfil. Permitiría distinguir "frontal" de "girado". Se descarta porque **resuelve el problema equivocado**: no necesitamos saber el ángulo, necesitamos que las capturas sean variadas, y eso se mide directamente y con más certeza. Sumar un segundo clasificador agregaría un XML, una carga en el arranque y una fuente más de falsos positivos, a cambio de una señal que igual habría que corroborar.

### Estimar la pose con landmarks faciales (dlib, MediaPipe)

Daría la orientación real de la cabeza, con precisión. Se descarta por peso: son dependencias grandes, algunas con modelos preentrenados de licencia menos clara que OpenCV, para un sistema que se despliega en una máquina de escritorio de una escuela. La tolerancia que gana el modelo no justifica el salto de complejidad en esta etapa.

### Dejar que el administrador decida cuándo capturar, con un botón

Más simple de implementar. Se descarta porque **traslada el criterio técnico a quien no lo tiene**: nadie mirando una previsualización puede juzgar si la varianza del Laplaciano alcanza. Terminaría capturando cuando "se ve bien", que es exactamente el criterio que venía fallando.

### Aumentar la duración de la grabación

La respuesta obvia a "el modelo sale flojo" es grabar sesenta segundos en vez de treinta. Se descarta porque **duplica el problema en vez de resolverlo**: cuarenta fotos idénticas no son mejores que veinte.

## Consecuencias

### Positivas

- El modelo se entrena con poses variadas, que es lo que le da tolerancia real en el pase.
- Ninguna captura borrosa o mal iluminada entra al entrenamiento.
- Quien falla sabe qué corregir.
- El proceso ya no puede "terminar bien" con un resultado malo: o cumple, o lo dice.
- Las mediciones quedan en el log, así que los umbrales se pueden calibrar con datos en vez de a ojo.

### Negativas y limitaciones

- **Los umbrales de calidad dependen de la cámara y del lugar.** Los valores por defecto son razonables pero no universales: una cámara con poco contraste puede rechazar capturas buenas. Son configurables y se calibran igual que el umbral de reconocimiento.
- **El proceso tarda más y puede frustrar.** Antes eran treinta segundos pasara lo que pasara; ahora, con mala luz, puede no completarse hasta que se mejore la luz. Es intencional, pero es fricción real.
- **Una llamada al servidor cada 600 ms** durante todo el registro. Aceptable en una red local, pero es más tráfico que antes.
- La variación que se logra sigue siendo **moderada por diseño**. Esto no vuelve al sistema invariante a la pose: mejora la tolerancia dentro de un rango, no la elimina como problema.
- **No distingue a la persona de su fotografía.** Un modelo mejor entrenado reconoce mejor una fotografía sostenida frente a la cámara, igual que reconoce mejor a la persona: entrenar mejor no ayuda con esto.

## Referencias

- [ADR-0007: Reconocimiento facial con JavaCV + LBPH](./0007-reconocimiento-facial-lbph.md) — por qué LBPH y sus límites.
- `CalidadCapturaService` — las métricas y el criterio de diferencia entre capturas.
- `EtapaCaptura` — las poses, con el motivo de que sean suaves.
- `ModeloFacialServiceTest` — tests de descarte por calidad y por pose repetida.
- [Protocolo de calibración](../../7-informes/calibracion-umbral.md) — cómo ajustar los umbrales con datos.
