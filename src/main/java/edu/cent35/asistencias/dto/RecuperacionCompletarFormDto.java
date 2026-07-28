package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form que cierra la recuperación: el código recibido más la contraseña nueva. Mantiene la
 * misma política de largo que el reset que hace el superadmin, para no tener dos reglas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecuperacionCompletarFormDto {

    // Mismas reglas que CodigoFormDto: seis dígitos, tolerando separadores al pegarlo. El tope
    // de largo es un resguardo con mensaje propio, para que no se muestre repetido.
    @NotBlank(message = "Ingresá el código que te llegó por correo")
    @Size(max = 50, message = "El código recibido es mucho más corto que eso")
    @Pattern(regexp = "^[\\s.-]*(\\d[\\s.-]*){6}$",
             message = "El código tiene seis dígitos")
    private String codigo;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, max = 60, message = "La contraseña debe tener entre 6 y 60 caracteres")
    private String nuevaPassword;

    // Mismo tope que la contraseña: si no coincide se rechaza igual, pero no tiene sentido
    // aceptar un texto de largo distinto al del campo que se está repitiendo.
    @NotBlank(message = "Repetí la contraseña")
    @Size(max = 60, message = "La contraseña debe tener entre 6 y 60 caracteres")
    private String confirmacion;

    // Indica si la contraseña y su repetición son iguales.
    public boolean coincide() {
        return nuevaPassword != null && nuevaPassword.equals(confirmacion);
    }
}
