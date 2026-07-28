package edu.cent35.asistencias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form del código de seis dígitos que llega por correo. El patrón exige exactamente seis
 * dígitos pero tolera espacios, puntos y guiones entre medio, para que se pueda pegar tal
 * como aparece en el mensaje sin tener que limpiarlo a mano.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodigoFormDto {

    // Los dos límites cumplen roles distintos: el patrón es la regla que ve la persona, y el
    // tope de largo es un resguardo contra un envío enorme, que en uso normal nunca se alcanza.
    // Por eso llevan mensajes distintos: si compartieran uno, se mostraría repetido.
    @NotBlank(message = "Ingresá el código que te llegó por correo")
    @Size(max = 50, message = "El código recibido es mucho más corto que eso")
    @Pattern(regexp = "^[\\s.-]*(\\d[\\s.-]*){6}$",
             message = "El código tiene seis dígitos")
    private String codigo;
}
