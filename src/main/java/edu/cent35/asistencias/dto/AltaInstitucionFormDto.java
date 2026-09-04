package edu.cent35.asistencias.dto;

import edu.cent35.asistencias.validacion.CuitValido;
import edu.cent35.asistencias.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del alta de una institución nueva junto con su primera cuenta. Nada de esto se guarda
 * al enviar el formulario: queda en espera hasta que se valide el código enviado al correo,
 * de modo que una institución solo llega a existir con su dirección ya comprobada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AltaInstitucionFormDto {

    // ---- Institución ----

    @NotBlank(message = "El nombre de la institución es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String nombreInstitucion;

    // Se aceptan las dos formas en que la gente lo escribe: con guiones o de corrido.
    // Exigir una sola era rechazar un dato correcto por un detalle de tipeo. Antes de
    // guardarlo se lleva siempre a la forma con guiones (ver Cuit.normalizar).
    @Pattern(regexp = "^$|^[0-9]{11}$|^[0-9]{2}-[0-9]{8}-[0-9]$",
             message = "El CUIT tiene que ser 11 dígitos: 30-12345678-1 o 30123456781")
    @CuitValido
    @Size(max = 13, message = "El CUIT no puede superar 13 caracteres")
    private String cuit;

    // ---- Primera cuenta (rol INSTITUCION) ----

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 3, max = 60, message = "El usuario debe tener entre 3 y 60 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "El usuario solo admite letras, números, punto, guion y guion bajo")
    private String username;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe ser válido")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @PasswordSegura
    private String password;

    @NotBlank(message = "Repetí la contraseña")
    private String confirmacion;

    // Acá estaban el nombre y el apellido de una persona, y se sacaron en V018. Lo que se da de
    // alta es un establecimiento, no alguien: la cuenta que nace de este formulario representa a
    // la institución. Las personas concretas que la administran se cargan después, desde adentro
    // del sistema, cada una con su propia cuenta y su identidad.

    // Indica si la contraseña y su repetición son iguales.
    public boolean coincide() {
        return password != null && password.equals(confirmacion);
    }
}
