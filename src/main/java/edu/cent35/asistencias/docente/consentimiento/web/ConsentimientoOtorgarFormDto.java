package edu.cent35.asistencias.docente.consentimiento.web;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form simplificado para que el admin registre el consentimiento biometrico
 * de un docente en representacion.
 * <p>
 * Por ahora solo expone el checkbox de confirmacion. El metodo (ESCRITO),
 * la fecha (hoy) y la URL del documento se asumen por defecto y se setean
 * en el controller. En el futuro el form podra incluir esos campos cuando
 * sea necesario (por ejemplo cuando se cargen consentimientos
 * retroactivos).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentimientoOtorgarFormDto {

    /**
     * Casilla "He leído y acepto el texto en nombre del docente que firmó".
     * Es la salvaguarda de UI para que el admin no haga clic sin querer.
     */
    @AssertTrue(message = "Tenés que confirmar que el docente leyó y firmó el texto antes de registrar el consentimiento")
    private boolean aceptaTexto;
}
