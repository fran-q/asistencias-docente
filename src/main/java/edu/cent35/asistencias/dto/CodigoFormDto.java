package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form del código de seis dígitos que llega por correo. No valida el formato acá porque el
 * servicio ya limpia lo que no sean números, para tolerar que se pegue con espacios o guiones.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodigoFormDto {

    @NotBlank(message = "Ingresá el código que te llegó por correo")
    private String codigo;
}
