package com.turbotax.taxpayer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class PiiEncryptionService {

    private final KmsClient kmsClient;

    @Value("${aws.kms.pii-key-alias}")
    private String piiKeyAlias;

    public String encrypt(String plaintext) {
        SdkBytes ciphertext = kmsClient.encrypt(
            EncryptRequest.builder()
                .keyId(piiKeyAlias)
                .plaintext(SdkBytes.fromString(plaintext, StandardCharsets.UTF_8))
                .build()
        ).ciphertextBlob();
        return Base64.getEncoder().encodeToString(ciphertext.asByteArray());
    }

    public String decrypt(String base64Ciphertext) {
        SdkBytes ciphertext = SdkBytes.fromByteArray(Base64.getDecoder().decode(base64Ciphertext));
        return kmsClient.decrypt(
            DecryptRequest.builder()
                .keyId(piiKeyAlias)
                .ciphertextBlob(ciphertext)
                .build()
        ).plaintext().asUtf8String();
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
