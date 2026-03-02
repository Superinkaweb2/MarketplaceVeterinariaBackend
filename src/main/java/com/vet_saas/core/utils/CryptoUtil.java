package com.vet_saas.core.utils;

import com.vet_saas.core.exceptions.types.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";

    private final SecretKeySpec secretKey;

    public CryptoUtil(@Value("${app.security.encryption.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Security critical error: app.security.encryption.secret is not configured.");
        }
        if (secret.length() != 16 && secret.length() != 24 && secret.length() != 32) {
            // Hacemos un padding o recorte simple para asegurar la llave (esto es para
            // simplificar en SaaS)
            secret = String.format("%-32s", secret).substring(0, 32);
        }
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String encrypt(String valueToEnc) {
        if (valueToEnc == null || valueToEnc.trim().isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedValue = cipher.doFinal(valueToEnc.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedValue);
        } catch (Exception e) {
            throw new BusinessException("Error cifrando credenciales sensibles");
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.trim().isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedValue = Base64.getDecoder().decode(encryptedValue);
            byte[] decValue = cipher.doFinal(decodedValue);
            return new String(decValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("Error descifrando credenciales sensibles");
        }
    }
}
