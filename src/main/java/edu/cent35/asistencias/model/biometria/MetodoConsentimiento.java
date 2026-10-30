package edu.cent35.asistencias.model.biometria;

/**
 * Cómo se obtuvo la firma del consentimiento.
 * <p>
 * Hoy el único camino es {@link #ESCRITO}: el docente firma en papel y el admin de la
 * institución carga el registro en representación. Hubo un {@code DIGITAL} reservado para cuando
 * el docente aceptara desde un login propio; ese login quedó fuera del alcance, y V035 lo sacó.
 * <p>
 * Coincide con el constraint CHECK ck_consentimientos_metodo (V035).
 */
public enum MetodoConsentimiento {

    // Firma manuscrita en papel; el admin carga el registro.
    ESCRITO
}
