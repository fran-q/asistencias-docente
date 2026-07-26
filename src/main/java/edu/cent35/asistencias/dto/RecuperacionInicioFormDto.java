package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form donde la persona escribe su usuario o su correo para pedir la recuperación. Se acepta
 * cualquiera de los dos porque a esta altura todavía no hay sesión y no se sabe cuál recuerda.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecuperacionInicioFormDto {

    @NotBlank(message = "Ingresá tu usuario o tu correo")
    private String usuarioOEmail;
}
