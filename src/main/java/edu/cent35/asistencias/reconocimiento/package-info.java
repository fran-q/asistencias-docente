/**
 * Modulo de reconocimiento facial.
 * <p>
 * Recibe frames del navegador (HTTP POST cada ~500ms desde la pantalla
 * de captura), detecta rostros con OpenCV (JavaCV), extrae embeddings,
 * matchea contra los modelos almacenados y devuelve la identidad del
 * docente con un nivel de confianza.
 * <p>
 * RF-15 a RF-20, RNF-01, RNF-16.
 */
package edu.cent35.asistencias.reconocimiento;
