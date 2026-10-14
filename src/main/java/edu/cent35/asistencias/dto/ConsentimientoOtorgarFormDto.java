package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form simplificado para que el admin registre el consentimiento biométrico
 * de un docente en representación.
 * <p>
 * Por ahora solo expone el checkbox de confirmación. El método (ESCRITO),
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

    // Casilla que el admin marca en nombre del docente que firmó; evita el clic distraído.
    @AssertTrue(message = "Tenés que confirmar que el docente leyó y firmó el texto antes de registrar el consentimiento")
    private boolean aceptaTexto;
}
