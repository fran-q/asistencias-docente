/**
 * Modulo de datos biometricos del docente.
 * <p>
 * Subdivisiones:
 * <ul>
 *   <li>{@code consentimiento}: alta y revocacion del consentimiento (RF-10, RNF-13).</li>
 *   <li>{@code modelo}: embeddings faciales cifrados (RF-08, RF-09, RNF-07, RNF-08).</li>
 * </ul>
 * <p>
 * Cumplimiento legal: Ley 25.326 y Resolucion AAIP 255/2022. Nunca se
 * almacenan fotografias - solo vectores numericos cifrados.
 */
package edu.cent35.asistencias.biometria;
