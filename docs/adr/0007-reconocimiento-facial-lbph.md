# ADR-0007: Reconocimiento facial con JavaCV + LBPH

**Estado**: Aceptada
**Fecha**: 2026-05-21
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

Sprint 4 implementa el PoC de reconocimiento facial (RF-08, RF-09). El sistema debe poder:

1. Registrar el rostro de un docente como modelo biométrico.
2. Dada una imagen nueva, identificar a qué docente corresponde (o ninguno).

Restricciones del proyecto:

- **Java obligatorio** (no se puede delegar a un servicio Python).
- **Todo open source**.
- **Marco legal** (Ley 25.326 / AAIP 255/2022): NO se almacenan fotografías; solo una representación matemática, cifrada. Las imágenes captadas se descartan tras generar el modelo.
- Requiere consentimiento biométrico ACTIVO del docente (ADR-0005).

## Decisiones

### 1. Librería: JavaCV (wrapper de OpenCV)

`org.bytedeco:javacv:1.5.11` + `org.bytedeco:opencv-platform:4.10.0-1.5.11`. JavaCV es el binding Java de OpenCV mantenido por Bytedeco. `opencv-platform` incluye los módulos *contrib*, entre ellos `opencv_face` (necesario para LBPH).

**Alternativas descartadas**:
- *Deep Java Library (DJL) + modelo ONNX*: más preciso (embeddings tipo FaceNet/ArcFace) pero más complejo de configurar y de explicar en una defensa académica.
- *Servicio externo Python*: viola la restricción "reconocimiento en Java".

### 2. Restricción de plataforma nativa

`gradle.properties` fija `javacpp.platform=windows-x86_64`. Sin esto, Gradle descarga ~1 GB de binarios de todas las plataformas. Con la restricción baja solo los de Windows (~150 MB).

**Consecuencia**: si el CI corre en Linux hay que sobreescribir con `-Pjavacpp.platform=linux-x86_64`. Documentado en el propio `gradle.properties`.

### 3. Algoritmo: LBPH (Local Binary Patterns Histograms)

`LBPHFaceRecognizer` del módulo `opencv_face`. Entrena en CPU, sin GPU ni modelos pre-entrenados externos, simple de explicar.

**Alternativa descartada**: embeddings con red neuronal (FaceNet/SFace). Más preciso pero exige cargar un modelo `.onnx`, más superficie de configuración. Para un PoC académico con 200-400 docentes por institución, LBPH es suficiente.

### 4. Un modelo LBPH **por docente**

LBPH no produce "embeddings" individuales comparables por distancia (como FaceNet). Produce un modelo entrenado. Para encajar con la tabla `modelos_faciales` (un registro por docente):

- Al registrar al docente X se capturan **N frames** de su rostro (vía webcam).
- Cada frame: detección de rostro (Haar Cascade) → recorte → escala de grises → redimensión a tamaño fijo.
- Se entrena un `LBPHFaceRecognizer` **solo con los N rostros de X**, todos con el mismo label.
- El modelo se serializa (`recognizer.save()`), se **cifra** y se guarda como BLOB en `modelos_faciales.embedding_cifrado`.

**Verificación**: dada una imagen nueva, se cargan los modelos de los docentes activos del tenant, se hace `predict()` con cada uno y se elige el de **menor distancia** (mejor match) que esté por debajo de un umbral de confianza. Si ninguno pasa el umbral → "no reconocido".

**Limitación conocida**: la verificación es O(N) modelos. Para el PoC de Sprint 4 (que no marca asistencia) es aceptable. Sprint 5 puede optimizar (cache de modelos en memoria, índice, o migración a un único modelo multi-label por institución).

**Alternativa descartada**: un único `LBPHFaceRecognizer` global por institución, cada docente un label. Más eficiente en verificación, pero no encaja con la tabla `modelos_faciales` (un BLOB por docente) y complica el re-registro/baja individual (RF-09).

### 5. Captura de imagen: webcam del navegador

El navegador accede a la cámara con `getUserMedia()`, captura frames y los envía al servidor. Es lo más cercano al uso real (un docente parándose frente a una cámara). El procesamiento (detección, recorte, entrenamiento) ocurre **en el servidor**, en Java.

### 6. Cifrado del modelo biométrico

El modelo LBPH serializado se cifra con **Spring Security Crypto** (AES) antes de persistirlo. La clave y el salt se configuran en `application-local.properties` (no versionado). Cumple el requisito de "datos biométricos cifrados" del marco legal.

### 7. No se almacenan imágenes

Las imágenes captadas viven en memoria el tiempo necesario para entrenar/verificar y se descartan. Nunca se persisten a disco ni a BD. La única huella biométrica persistida es el modelo LBPH cifrado.

## Estado de validación

- Dependencia JavaCV agregada, `./gradlew build` correcto.
- `OpenCvSmokeTest` verde: los binarios nativos de OpenCV cargan en Windows y `LBPHFaceRecognizer.create()` funciona.
- Pipeline completo testeado manualmente en Sprint 4:
    1. Registro: grabación de 30 s → 20 frames → ~17 rostros válidos → LBPH entrenado → cifrado → persistido (~250 KB de BLOB cifrado típico).
    2. Identificación en vivo: pantalla `/reconocimiento/prueba` con loop continuo cada 700 ms reconoce el docente registrado con recuadro verde + nombre. Sin internet, falla con elegancia (no rompe la UI).
- Tests unitarios de `CifradoBiometricoServiceTest`, `ModeloFacialServiceTest`, `IdentificacionFacialServiceTest`.

## Aprendizajes durante la implementación

Cosas no obvias que surgieron y se documentan acá para que no se repitan:

1. **Hibernate 6 + `@Lob byte[]` mapea a `BLOB`/`TINYBLOB`, no a `LONGBLOB`** como hacía Hibernate 5. Para que `ddl-auto=validate` coincida con `LONGBLOB` (necesario para el modelo LBPH cifrado) hubo que anotar el campo con `@JdbcTypeCode(SqlTypes.LONGVARBINARY)` en vez de `@Lob`.

2. **El YAML serializado de OpenCV es enorme y altamente repetitivo**. Sin compresión, el INSERT con el modelo cifrado superaba el `max_allowed_packet` default de MariaDB y MariaDB cerraba la conexión sucia, corrompiendo tablas del sistema (`mysql.global_priv`). Mitigación: **gzip antes de cifrar** (5-10× menos bytes) + subir `max_allowed_packet` a 64 MB en `my.ini`.

3. **El tipo `dimensiones` de la columna es `SMALLINT`** (heredado de V001, pensado para vectores embedding chicos). La entidad lo mapea como `Short` para que Hibernate `validate` no se queje.

4. **`spring.jpa.open-in-view=false`** del proyecto exige tocar las asociaciones LAZY dentro de la transacción. El service hace `touchLazy` y la query `findActivosDelTenant` usa `JOIN FETCH` para que el `Docente` viaje cargado.

5. **Memoria nativa de OpenCV**: cada `Mat`, `RectVector`, `LBPHFaceRecognizer` reserva memoria nativa fuera del heap de la JVM. Hay que cerrarlos explícitamente (try-with-resources o `.close()` en `finally`). Los recognizers cacheados en `IdentificacionFacialService` se cierran al ser evictados.

6. **POSTs grandes**: el body con 20 frames base64 puede pesar 2-3 MB. Hubo que subir `server.tomcat.max-http-form-post-size=20MB` y `max-swallow-size=20MB`.

7. **Cache de recognizers**: descifrar + descomprimir + deserializar un LBPH por cada llamada hace inviable el loop continuo. `IdentificacionFacialService` mantiene un `ConcurrentHashMap` de recognizers cargados, sincronizado por cada llamada (el `JOIN FETCH` ya trae los activos del tenant; los desaparecidos se evictan).

8. **Calibración del umbral**: LBPH devuelve "distancia" (menor = más parecido). Por default usamos `app.biometria.umbral-confianza=100.0`. Suele dar match decente para una persona registrada con 10-15 capturas y reconocida en condiciones de iluminación similares. Se puede subir/bajar según calibración.

## Desviación aceptada del RF-08 (decisión formal, post-cierre)

El RF-08, tal como quedó tras la entrevista de requerimientos, expresa preferencia por
**embeddings de deep learning** (FaceNet/ArcFace/SFace) por sobre el reconocedor clásico
de OpenCV. La implementación usa **LBPH**, que no genera un embedding reutilizable sino
un modelo entrenado por docente. Esa diferencia se formaliza acá como **desviación
aceptada de alcance del prototipo**, con estos términos:

- **Qué se cumple igual**: no se almacenan imágenes (RNF-08), el dato biométrico se
  persiste cifrado (RNF-07), el re-registro (RF-09) y la supresión ARCO (RNF-14)
  funcionan, y el criterio de minimizar falsos positivos rige el umbral (RF-16).
- **Qué no se cumple literalmente**: el "embedding reutilizable" del RF-08. El modelo
  LBPH es funcionalmente equivalente para el flujo del PoC, pero es más sensible a
  iluminación/pose y su verificación 1:N es más pesada.
- **Camino de migración identificado y verificado**: los bindings de JavaCV ya incluidos
  en el classpath (`opencv-4.10.0-1.5.11.jar`) contienen `FaceRecognizerSF` (SFace,
  embeddings de 128 dims con similitud coseno) y `FaceDetectorYN` (YuNet). La migración
  **no requiere dependencias nuevas**: solo el modelo `.onnx` del zoo de OpenCV y el
  reemplazo de `MotorLbphService` por un motor de embeddings. La tabla `modelos_faciales`
  no cambia (el vector cifrado de 512 bytes entra en el mismo BLOB; `dimensiones` pasa a
  valer 128 con su semántica original).
- **Estado**: migración planificada como trabajo futuro, no incluida en la primera
  entrega. La defensa del proyecto presenta LBPH como decisión consciente de prototipo.

## Supresión física ARCO (RNF-14) — excepción al borrado lógico

Ante el ejercicio del derecho de **Cancelación** (Ley 25.326), el vector biométrico se
borra **físicamente** — única excepción a la regla general de baja lógica del sistema.
`ModeloFacialService.suprimirDatosBiometricos()` elimina todos los modelos del docente
(activo e históricos), evicta los recognizers del cache en memoria (para que no quede
una copia deserializada capaz de reconocer), y conserva las asistencias históricas: la
FK `asistencias.modelo_facial_id` es `ON DELETE SET NULL` desde V001, de modo que el
registro administrativo queda intacto y deja de ser dato biométrico.

## Consecuencias

**Positivas**:
- 100% Java, open source, sin servicios externos.
- LBPH es simple de implementar, entrenar y explicar.
- El diseño por-docente encaja con la tabla `modelos_faciales` existente y soporta re-registro individual (RF-09).
- Cifrado + no-persistencia de imágenes cumple Ley 25.326 / AAIP 255/2022.

**Negativas**:
- LBPH es sensible a iluminación y pose; menos robusto que un enfoque por embeddings. Aceptable para un PoC.
- Verificación O(N): no escala a miles de docentes sin optimización (anotado para Sprint 5).
- `javacpp.platform` fija la plataforma: el build local es Windows-only salvo override.
- La columna `dimensiones` de `modelos_faciales` fue pensada para embeddings de vector fijo; con LBPH se reutiliza para guardar el tamaño de la imagen normalizada.

## Referencias

- ADR-0005 — Consentimiento biométrico (prerequisito del registro facial).
- RF-08 (registro facial), RF-09 (re-registro), RNF-07/08 (seguridad biométrica).
- Documentación OpenCV: módulo `face`, clase `LBPHFaceRecognizer`.
- Ley N° 25.326 y Resolución AAIP N° 255/2022.
