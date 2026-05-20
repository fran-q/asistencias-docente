package edu.cent35.asistencias.model;

/**
 * Como se obtuvo la firma del consentimiento.
 * <p>
 * En Sprint 3 solo se usa {@link #ESCRITO}: el docente firma en papel y el
 * admin de la institucion carga el registro en representacion. En Sprint 4,
 * cuando exista login docente, se habilitara {@link #DIGITAL} con aceptacion
 * por clic + checkbox.
 * <p>
 * Coincide con el constraint CHECK ck_consentimientos_metodo de V001.
 */
public enum MetodoConsentimiento {

    /** Firma manuscrita en papel; el admin carga el registro. */
    ESCRITO,

    /** Aceptacion digital del propio docente (Sprint 4+). */
    DIGITAL
}
