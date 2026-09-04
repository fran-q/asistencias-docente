package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.ImpactoIdentidadDto;
import lombok.Getter;

/**
 * La operación se detuvo porque alcanza a una identidad ya existente y hace falta que alguien
 * confirme que es la que se quiere tocar (ADR-0016).
 * No es un error: es una pausa. Lleva adentro el detalle de a quién alcanza para que la pantalla
 * pueda mostrarlo, y el mismo pedido vuelve a llegar con {@code confirmado} en true.
 */
@Getter
public class ConfirmacionRequeridaException extends RuntimeException {

    private final transient ImpactoIdentidadDto impacto;

    public ConfirmacionRequeridaException(ImpactoIdentidadDto impacto) {
        super("Se requiere confirmación sobre la identidad " + impacto.getPersonaId());
        this.impacto = impacto;
    }
}
