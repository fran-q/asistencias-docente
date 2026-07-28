package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // El tope sale del campo más largo que puede llegar: el correo son 120 caracteres en la
    // base y el usuario 60. No se valida el formato porque acá entran los dos indistintamente.
    @NotBlank(message = "Ingresá tu usuario o tu correo")
    @Size(max = 120, message = "El usuario o correo no puede superar los 120 caracteres")
    private String usuarioOEmail;
}
