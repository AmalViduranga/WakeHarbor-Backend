package io.wakeharbor.control.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("my_super_secret_test_encryption_key_2026");
    }

    @Test
    @DisplayName("Encrypt and decrypt roundtrip should preserve plaintext and authenticate integrity")
    void testEncryptionRoundtrip() {
        String originalKey = "sb_publishable_abcdef1234567890_test_key";

        EncryptionService.EncryptedResult result = encryptionService.encrypt(originalKey);

        assertNotNull(result);
        assertNotNull(result.cipherTextBase64());
        assertNotNull(result.ivHex());
        assertNotNull(result.authTagHex());
        assertEquals("sb_pub..._key", result.preview());
        assertNotEquals(originalKey, result.cipherTextBase64());

        // Decrypt
        String decrypted = encryptionService.decrypt(result.cipherTextBase64(), result.ivHex(), result.authTagHex());
        assertEquals(originalKey, decrypted);
    }

    @Test
    @DisplayName("Tampered ciphertext or auth tag must throw integrity exception")
    void testTamperedCiphertext() {
        String originalKey = "sb_publishable_secret_api_key";
        EncryptionService.EncryptedResult result = encryptionService.encrypt(originalKey);

        // Tamper with IV or Auth Tag
        String tamperedTag = result.authTagHex().substring(0, result.authTagHex().length() - 2) + "00";

        assertThrows(RuntimeException.class, () ->
                encryptionService.decrypt(result.cipherTextBase64(), result.ivHex(), tamperedTag)
        );
    }

    @Test
    @DisplayName("Preview generation handles different key formats cleanly")
    void testKeyPreviewGeneration() {
        assertEquals("sb_pub...9f2a", encryptionService.generatePreview("sb_publishable_test_project_9f2a"));
        assertEquals("anon_k...1234", encryptionService.generatePreview("anon_key_long_string_1234"));
        assertEquals("sb_***", encryptionService.generatePreview("short"));
    }
}
