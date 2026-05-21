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

## Estado de validación (Fase A)

- Dependencia agregada, `./gradlew build` correcto.
- `OpenCvSmokeTest` verde: los binarios nativos de OpenCV cargan en Windows y `LBPHFaceRecognizer.create()` funciona (módulo contrib presente).

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
