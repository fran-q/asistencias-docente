package edu.cent35.asistencias.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;

/**
 * Cifra y descifra los modelos biométricos antes de persistirlos.
 * <p>
 * Usa AES vía Spring Security Crypto ({@code Encryptors.stronger}, que
 * deriva la clave con PBKDF2 y cifra en modo GCM). Cumple el requisito de
 * la Resolución AAIP 255/2022 de proteger los datos biométricos con
 * "medidas de seguridad razonables".
 * <p>
 * La clave y el salt se configuran en {@code application.properties}
 * ({@code app.biometria.*}); en un despliegue real deben venir de
 * variables de entorno. Ver ADR-0007.
 */
@Service
public class CifradoBiometricoService {

    private final BytesEncryptor encryptor;

    public CifradoBiometricoService(
            @Value("${app.biometria.clave-cifrado}") String clave,
            @Value("${app.biometria.salt}") String salt) {
        // Encryptors.stronger -> AES-256-GCM con clave derivada por PBKDF2.
        this.encryptor = Encryptors.stronger(clave, salt);
    }

    // Cifra datos en claro (ej. el modelo LBPH serializado).
    public byte[] cifrar(byte[] datosEnClaro) {
        return encryptor.encrypt(datosEnClaro);
    }

    // Descifra datos previamente cifrados con {@link #cifrar(byte[])}.
    public byte[] descifrar(byte[] datosCifrados) {
        return encryptor.decrypt(datosCifrados);
    }
}
