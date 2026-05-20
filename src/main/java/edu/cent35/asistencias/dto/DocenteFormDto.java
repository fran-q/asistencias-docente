package edu.cent35.asistencias.dto;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.model.Docente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocenteFormDto {

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^[0-9]{7,12}$", message = "El DNI debe tener entre 7 y 12 dígitos numéricos")
    @Size(max = 15)
    private String dni;

    @Size(max = 30, message = "El legajo no puede superar 30 caracteres")
    private String legajo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 80)
    private String apellido;

    @Email(message = "El email debe ser válido")
    @Size(max = 120)
    private String email;

    @Size(max = 30)
    private String telefono;

    @NotNull(message = "La fecha de alta es obligatoria")
    @PastOrPresent(message = "La fecha de alta no puede ser futura")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaAlta;

    public static DocenteFormDto from(Docente d) {
        return DocenteFormDto.builder()
            .dni(d.getDni())
            .legajo(d.getLegajo())
            .nombre(d.getNombre())
            .apellido(d.getApellido())
            .email(d.getEmail())
            .telefono(d.getTelefono())
            .fechaAlta(d.getFechaAlta())
            .build();
    }
}
