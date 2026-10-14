package edu.cent35.asistencias.dto.biometria;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form para revocar el consentimiento biométrico vigente de un docente.
 * <p>
 * El motivo es opcional pero recomendado (derecho ARCO - RNF-14): si el
 * docente expresa el motivo de la revocación, conviene registrarlo para
 * auditoría. El checkbox obliga a una confirmación explícita igual que en
 * el otorgamiento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentimientoRevocarFormDto {

    // Motivo opcional, texto libre (derecho ARCO).
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String motivo;

    // Confirmación explícita: misma salvaguarda que en el otorgamiento.
    @AssertTrue(message = "Tenés que confirmar la revocación tildando la casilla")
    private boolean confirmaRevocacion;
}
