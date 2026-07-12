package com.turbotax.refund.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotax.refund.service.SecretsManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretsManagerServiceTest {

    @Mock SecretsManagerClient secretsManagerClient;

    SecretsManagerService service;

    @BeforeEach
    void setup() {
        service = new SecretsManagerService(secretsManagerClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "jwtSecretName", "dev/turbotax/jwt");
    }

    private GetSecretValueResponse response(String json) {
        return GetSecretValueResponse.builder().secretString(json).build();
    }

    @Test
    void preload_shouldCacheSecret_onSuccess() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(response("{\"private_key\":\"pk\",\"public_key\":\"pub\"}"));

        service.preload();

        assertThat(service.getJwtPrivateKey()).isEqualTo("pk");
        assertThat(service.getJwtPublicKey()).isEqualTo("pub");
    }

    @Test
    void preload_shouldThrow_whenSecretsManagerFails() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        assertThatThrownBy(() -> service.preload())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start");
    }

    @Test
    void get_shouldEscapeLiteralNewlinesInsidePemStrings() {
        // A raw PEM key often contains literal newlines inside the JSON string value,
        // which is invalid JSON unless escaped -- verifies escapePemNewlines handles it.
        String rawWithLiteralNewlines = "{\"private_key\":\"-----BEGIN KEY-----\n" +
            "abc123\n" +
            "-----END KEY-----\",\"public_key\":\"pub\"}";
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(response(rawWithLiteralNewlines));

        String result = service.get("dev/turbotax/jwt", "private_key");

        assertThat(result).contains("BEGIN KEY").contains("abc123");
    }

    @Test
    void get_shouldEscapeLiteralCarriageReturnsInsidePemStrings() {
        String rawWithCarriageReturn = "{\"private_key\":\"-----BEGIN KEY-----\r" +
            "abc123\r" +
            "-----END KEY-----\",\"public_key\":\"pub\"}";
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(response(rawWithCarriageReturn));

        String result = service.get("dev/turbotax/jwt", "private_key");

        assertThat(result).contains("BEGIN KEY").contains("abc123");
    }

    @Test
    void get_shouldThrow_whenKeyNotFoundInSecret() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(response("{\"other_key\":\"value\"}"));

        assertThatThrownBy(() -> service.get("dev/turbotax/jwt", "missing_key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing_key");
    }

    @Test
    void get_shouldThrow_whenSecretCannotBeLoaded() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        assertThatThrownBy(() -> service.get("does/not/exist", "key"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load secret");
    }

    @Test
    void get_shouldCacheAcrossCalls_soSecretsManagerIsCalledOnce() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(response("{\"private_key\":\"pk\"}"));

        service.get("dev/turbotax/jwt", "private_key");
        service.get("dev/turbotax/jwt", "private_key");

        org.mockito.Mockito.verify(secretsManagerClient, org.mockito.Mockito.times(1))
            .getSecretValue(any(GetSecretValueRequest.class));
    }
}
