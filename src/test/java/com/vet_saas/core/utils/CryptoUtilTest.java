package com.vet_saas.core.utils;

import com.vet_saas.core.exceptions.types.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    private final CryptoUtil cryptoUtil = new CryptoUtil("test-encryption-secret-at-least-32-chars-long");

    @Test
    void encrypt_andDecrypt_roundtrip() {
        String original = "super-secret-mp-access-token";
        String encrypted = cryptoUtil.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = cryptoUtil.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertNull(cryptoUtil.encrypt(null));
    }

    @Test
    void encrypt_blankInput_returnsNull() {
        assertNull(cryptoUtil.encrypt("   "));
    }

    @Test
    void decrypt_nullInput_returnsNull() {
        assertNull(cryptoUtil.decrypt(null));
    }

    @Test
    void decrypt_blankInput_returnsNull() {
        assertNull(cryptoUtil.decrypt("   "));
    }

    @Test
    void encrypt_differentOutputs_eachTime() {
        String original = "consistent-plain-text";
        String enc1 = cryptoUtil.encrypt(original);
        String enc2 = cryptoUtil.encrypt(original);
        assertNotEquals(enc1, enc2);
        assertEquals(original, cryptoUtil.decrypt(enc1));
        assertEquals(original, cryptoUtil.decrypt(enc2));
    }

    @Test
    void decrypt_tamperedData_throwsBusinessException() {
        String encrypted = cryptoUtil.encrypt("some-value");
        assertNotNull(encrypted);
        String tampered = encrypted.substring(0, encrypted.length() - 5) + "XXXXX";
        assertThrows(BusinessException.class, () -> cryptoUtil.decrypt(tampered));
    }

    @Test
    void encrypt_shortSecret_padsTo32Bytes() {
        CryptoUtil shortKeyCrypto = new CryptoUtil("short-key");
        String result = shortKeyCrypto.encrypt("test");
        assertNotNull(result);
        assertEquals("test", shortKeyCrypto.decrypt(result));
    }

    @Test
    void constructor_blankSecret_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> new CryptoUtil(""));
        assertThrows(IllegalStateException.class, () -> new CryptoUtil(null));
    }
}
