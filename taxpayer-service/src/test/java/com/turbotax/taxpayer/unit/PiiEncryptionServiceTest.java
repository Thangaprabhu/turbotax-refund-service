package com.turbotax.taxpayer.unit;

import com.turbotax.taxpayer.service.PiiEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PiiEncryptionServiceTest {

    @Mock KmsClient kmsClient;

    PiiEncryptionService service;

    @BeforeEach
    void setup() {
        service = new PiiEncryptionService(kmsClient);
        ReflectionTestUtils.setField(service, "piiKeyAlias", "alias/turbotax-pii");
    }

    @Test
    void encrypt_shouldReturnBase64OfKmsCiphertext() {
        ArgumentCaptor<EncryptRequest> captor = ArgumentCaptor.forClass(EncryptRequest.class);
        when(kmsClient.encrypt(captor.capture())).thenReturn(
            EncryptResponse.builder()
                .ciphertextBlob(SdkBytes.fromUtf8String("cipher-bytes"))
                .build()
        );

        String result = service.encrypt("123-45-6789");

        assertThat(result).isEqualTo(Base64.getEncoder().encodeToString("cipher-bytes".getBytes(StandardCharsets.UTF_8)));
        assertThat(captor.getValue().keyId()).isEqualTo("alias/turbotax-pii");
        assertThat(captor.getValue().plaintext().asUtf8String()).isEqualTo("123-45-6789");
    }

    @Test
    void decrypt_shouldReturnPlaintext_fromBase64Ciphertext() {
        String base64Ciphertext = Base64.getEncoder().encodeToString("cipher-bytes".getBytes(StandardCharsets.UTF_8));
        ArgumentCaptor<DecryptRequest> captor = ArgumentCaptor.forClass(DecryptRequest.class);
        when(kmsClient.decrypt(captor.capture())).thenReturn(
            DecryptResponse.builder()
                .plaintext(SdkBytes.fromUtf8String("123-45-6789"))
                .build()
        );

        String result = service.decrypt(base64Ciphertext);

        assertThat(result).isEqualTo("123-45-6789");
        assertThat(captor.getValue().keyId()).isEqualTo("alias/turbotax-pii");
        assertThat(captor.getValue().ciphertextBlob().asUtf8String()).isEqualTo("cipher-bytes");
    }

    @Test
    void hash_shouldReturnKnownSha256Digest() {
        // SHA-256("hello") is a well-known test vector.
        String result = service.hash("hello");

        assertThat(result).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void hash_shouldBeDeterministic() {
        assertThat(service.hash("123-45-6789")).isEqualTo(service.hash("123-45-6789"));
        assertThat(service.hash("123-45-6789")).isNotEqualTo(service.hash("123-45-6788"));
    }
}
