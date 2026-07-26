# Consentimiento informado para el tratamiento de datos biométricos

**Versión**: `2026-05-v1`
**Estado**: vigente

> Este documento es una copia impresa del texto que la aplicación muestra al docente en la
> pantalla de otorgamiento del consentimiento. **La fuente de verdad es el código**, en
> `src/main/java/edu/cent35/asistencias/service/TextoConsentimiento.java`: el sistema lo
> renderiza desde ahí y guarda, junto a cada aceptación, la versión que estaba vigente en ese
> momento. Si el texto cambia hay que incrementar la versión y regenerar esta copia.

---

```
CONSENTIMIENTO INFORMADO PARA EL TRATAMIENTO DE DATOS BIOMETRICOS
(Ley Nacional N° 25.326 de Proteccion de Datos Personales y
Resolucion AAIP N° 255/2022)

1. RESPONSABLE DEL TRATAMIENTO
   La institucion educativa identificada en este sistema es la
   responsable del tratamiento de los datos personales y biometricos
   del docente cuya firma figura al pie. Para cualquier consulta o
   ejercicio de los derechos ARCO (acceso, rectificacion, cancelacion
   y oposicion), el docente debe dirigirse a la administracion de su
   institucion.

2. FINALIDAD DEL TRATAMIENTO
   Los datos biometricos faciales del docente seran utilizados
   exclusivamente para verificar su presencia en clase mediante un
   sistema automatizado de reconocimiento facial. No se utilizaran
   para ninguna otra finalidad, ni se compartiran con terceros.

3. DATOS QUE SE TRATARAN
   El sistema procesa una representacion matematica (embedding) del
   rostro del docente, NO una fotografia. Los embeddings se almacenan
   cifrados y no permiten reconstruir la imagen original. Las
   imagenes captadas durante el proceso de registro inicial se
   descartan inmediatamente despues de generar el embedding.

4. CONSERVACION
   Los datos biometricos se conservaran mientras dure la relacion
   laboral del docente con la institucion. Una vez finalizada, los
   datos seran eliminados de forma segura dentro de los treinta (30)
   dias corridos.

5. DERECHOS DEL DOCENTE
   El docente puede en cualquier momento:
   - Acceder a los datos que el sistema tiene sobre el.
   - Pedir su rectificacion si fueran inexactos.
   - Solicitar la cancelacion de su modelo biometrico, lo que
     implica que dejara de poder usarse el reconocimiento facial
     para registrar su asistencia.
   - REVOCAR este consentimiento en cualquier momento, sin necesidad
     de justificar la decision y sin que ello le acarree perjuicios.

6. CARACTER VOLUNTARIO
   El consentimiento es libre, expreso e informado. La negativa a
   otorgarlo no podra ser causa de discriminacion ni de sancion. En
   caso de no consentir, la institucion ofrecera un mecanismo
   alternativo de registro de asistencia.

7. AUDITORIA
   El sistema registra, con fines de prueba forense, la fecha, la
   hora, la direccion IP y el navegador desde donde se otorgo o
   revoco el presente consentimiento.

Habiendo leido y comprendido lo anterior, OTORGO mi consentimiento
libre, expreso e informado para el tratamiento de mis datos
biometricos con la finalidad indicada en el punto 2.
```

---

## Sobre el versionado

Cada registro en `consentimientos_biometricos` guarda en `version_terminos` la versión que
regía al aceptar. Las aceptaciones de versiones anteriores **siguen siendo válidas**: fueron
legales en su momento. El sistema puede sugerir volver a aceptar una versión nueva, pero no
invalida las previas de forma retroactiva.

## Documentos relacionados

- [ADR-0005: Diseño del consentimiento biométrico](../4-arquitectura/adr/0005-consentimiento-biometrico.md)
- [ADR-0007: Reconocimiento facial](../4-arquitectura/adr/0007-reconocimiento-facial-lbph.md) — incluye la supresión física por derecho ARCO.
