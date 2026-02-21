package com.moltrank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommitRevealEnvelopeCryptoService {

    private static final String ENVELOPE_VERSION = "v2";
    private static final String ENVELOPE_ALGORITHM = "AES-256-GCM";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final CommitSecurityProperties commitSecurityProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public String encryptForStorage(byte[] canonicalRevealPayload) {
        try {
            String keyId = requireActiveKeyId();
            byte[] keyBytes = lookupKeyBytes(keyId);
            byte[] iv = randomIv();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(storageAad(keyId));

            byte[] ciphertext = cipher.doFinal(canonicalRevealPayload);
            StorageEnvelope envelope = new StorageEnvelope(
                    ENVELOPE_VERSION,
                    ENVELOPE_ALGORITHM,
                    keyId,
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to encrypt reveal payload for storage", ex);
        }
    }

    public byte[] decryptFromStorageEnvelope(String storedValue) {
        try {
            StorageEnvelope envelope = objectMapper.readValue(storedValue, StorageEnvelope.class);
            if (!ENVELOPE_VERSION.equals(envelope.version())) {
                throw new IllegalArgumentException("Unsupported reveal envelope version: " + envelope.version());
            }
            if (!ENVELOPE_ALGORITHM.equals(envelope.alg())) {
                throw new IllegalArgumentException("Unsupported reveal envelope algorithm: " + envelope.alg());
            }

            byte[] keyBytes = lookupKeyBytes(envelope.kid());
            byte[] iv = decodeBase64("iv", envelope.iv());
            if (iv.length != GCM_IV_BYTES) {
                throw new IllegalArgumentException("Reveal envelope iv must be 12 bytes");
            }
            byte[] ciphertext = decodeBase64("ciphertext", envelope.ct());

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(storageAad(envelope.kid()));
            return cipher.doFinal(ciphertext);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decrypt reveal payload envelope", ex);
        }
    }

    public boolean isStorageEnvelope(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private String requireActiveKeyId() {
        String keyId = commitSecurityProperties.getActiveStorageKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Active storage key id is not configured");
        }
        return keyId.trim();
    }

    private byte[] lookupKeyBytes(String keyId) {
        Map<String, String> configuredKeys = commitSecurityProperties.getStorageKeys();
        if (configuredKeys == null) {
            throw new IllegalArgumentException("No storage keys are configured");
        }
        String encodedKey = configuredKeys.get(keyId);
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is not configured for key id: " + keyId);
        }
        byte[] keyBytes = decodeBase64("storage key", encodedKey);
        if (keyBytes.length != AES_KEY_BYTES) {
            throw new IllegalArgumentException("Storage key must decode to exactly 32 bytes");
        }
        return keyBytes;
    }

    private byte[] storageAad(String keyId) {
        return ("moltrank-reveal-envelope|" + ENVELOPE_VERSION + "|" + keyId).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeBase64(String fieldName, String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid base64 value for " + fieldName, ex);
        }
    }

    private byte[] randomIv() {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private record StorageEnvelope(
            String version,
            String alg,
            String kid,
            String iv,
            String ct
    ) {
    }
}
