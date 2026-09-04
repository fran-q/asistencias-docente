package edu.cent35.asistencias.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Alta de institución esperando que se valide su código. Vive en la sesión del navegador y no
 * en la base: todavía no hay institución ni usuario a los cuales asociarla, y su vida útil son
 * los quince minutos que dura el código.
 */
public class AltaPendiente implements Serializable {

    private static final long serialVersionUID = 1L;

    private final AltaInstitucionFormDto datos;
    private final String codigoHash;
    private final LocalDateTime expiraEn;
    private int intentos;

    public AltaPendiente(AltaInstitucionFormDto datos, String codigoHash, LocalDateTime expiraEn) {
        this.datos = datos;
        this.codigoHash = codigoHash;
        this.expiraEn = expiraEn;
    }

    // Los datos tipeados en el paso 1, que recien se persisten al confirmar el codigo.
    public AltaInstitucionFormDto getDatos() {
        return datos;
    }

    // Hash del código; el código en claro solo viajó al correo y no se guarda en ningún lado.
    public String getCodigoHash() {
        return codigoHash;
    }

    // Correo al que se mandó el código, para poder mostrarlo en la pantalla de confirmación.
    public String getEmail() {
        return datos.getEmail();
    }

    // true si paso la ventana de vigencia y el alta ya no se puede confirmar.
    public boolean estaVencida() {
        return LocalDateTime.now().isAfter(expiraEn);
    }

    // Suma un intento fallido y responde cuántos van.
    public int sumarIntentoFallido() {
        return ++intentos;
    }

    // Cuantas veces se tipeo un codigo incorrecto para esta alta.
    public int getIntentos() {
        return intentos;
    }
}
