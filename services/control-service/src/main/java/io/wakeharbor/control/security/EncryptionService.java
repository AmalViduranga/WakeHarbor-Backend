package io.wakeharbor.control.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Provides AES-256-GCM authenticated encryption for sensitive credentials (such as Supabase API keys).
 * Generates an isolated 12-byte initialization vector (IV) per encryption.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey masterSecretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${wakeharbor.security.encryption-secret}") String rawSecret) {
        if (rawSecret == null || rawSecret.trim().length() < 16) {
            throw new IllegalArgumentException("WAKEHARBOR_ENCRYPTION_SECRET must be at least 16 characters long");
        }
        try {
            // Derive 256-bit key using SHA-256
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(rawSecret.trim().getBytes(StandardCharsets.UTF_8));
            this.masterSecretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize encryption service key", e);
        }
    }

    public record EncryptedResult(String cipherTextBase64, String ivHex, String authTagHex, String preview) {}

    /**
     * Encrypts plaintext data using AES-256-GCM and generates a masked preview.
     */
    public EncryptedResult encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterSecretKey, gcmSpec);

            byte[] cipherWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // In Java GCM, cipherWithTag has the ciphertext followed by the 16-byte authentication tag
            int cipherTextLen = cipherWithTag.length - 16;
            byte[] cipherBytes = new byte[cipherTextLen];
            byte[] tagBytes = new byte[16];
            System.arraycopy(cipherWithTag, 0, cipherBytes, 0, cipherTextLen);
            System.arraycopy(cipherWithTag, cipherTextLen, tagBytes, 0, 16);

            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherBytes);
            String ivHex = HexFormat.of().formatHex(iv);
            String authTagHex = HexFormat.of().formatHex(tagBytes);
            String preview = generatePreview(plainText);

            return new EncryptedResult(cipherTextBase64, ivHex, authTagHex, preview);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts ciphertext using stored IV and authentication tag.
     */
    public String decrypt(String cipherTextBase64, String ivHex, String authTagHex) {
        try {
            byte[] iv = HexFormat.of().parseHex(ivHex);
            byte[] tag = HexFormat.of().parseHex(authTagHex);
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);

            // Reconstruct GCM buffer (ciphertext + 16-byte tag)
            byte[] fullCipherWithTag = new byte[cipherBytes.length + tag.length];
            System.arraycopy(cipherBytes, 0, fullCipherWithTag, 0, cipherBytes.length);
            System.arraycopy(tag, 0, fullCipherWithTag, cipherBytes.length, tag.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterSecretKey, gcmSpec);

            byte[] decrypted = cipher.doFinal(fullCipherWithTag);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption or integrity verification failed", e);
        }
    }

    /**
     * Creates a safe preview without revealing full credentials (e.g. sb_pub...9a4f)
     */
    public String generatePreview(String rawKey) {
        if (rawKey == null || rawKey.length() < 10) {
            return "sb_***";
        }
        if (rawKey.startsWith("sb_publishable_")) {
            String suffix = rawKey.substring(rawKey.length() - 4);
            return "sb_pub..." + suffix;
        }
        String prefix = rawKey.substring(0, Math.min(6, rawKey.length()));
        String suffix = rawKey.substring(rawKey.length() - 4);
        return prefix + "..." + suffix;
    }
}
