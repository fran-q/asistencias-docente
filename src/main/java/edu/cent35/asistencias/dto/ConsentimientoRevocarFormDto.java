package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form para revocar el consentimiento biometrico vigente de un docente.
 * <p>
 * El motivo es opcional pero recomendado (derecho ARCO - RNF-14): si el
 * docente expresa el motivo de la revocacion, conviene registrarlo para
 * auditoria. El checkbox obliga a una confirmacion explicita igual que en
 * el otorgamiento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentimientoRevocarFormDto {

    /** Motivo opcional, texto libre (derecho ARCO). */
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String motivo;

    /** Confirmacion explicita: misma salvaguarda que en el otorgamiento. */
    @AssertTrue(message = "Tenés que confirmar la revocación tildando la casilla")
    private boolean confirmaRevocacion;
}
