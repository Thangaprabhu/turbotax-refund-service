package com.turbotax.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretsManagerService {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.secrets.jwt-secret-name}")
    private String jwtSecretName;

    // In-memory cache — fetched once at startup
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void preload() {
        log.info("Preloading secrets from AWS Secrets Manager");
        loadSecret(jwtSecretName);
        log.info("Secrets preloaded successfully");
    }

    @SuppressWarnings("unchecked")
    private void loadSecret(String secretName) {
        try {
            String raw = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();
            cache.put(secretName, objectMapper.readValue(escapePemNewlines(raw), Map.class));
            log.info("Loaded secret: {}", secretName);
        } catch (Exception e) {
            log.error("Failed to load secret: {}", secretName, e);
            throw new IllegalStateException("Cannot start — failed to load secret: " + secretName, e);
        }
    }

    /**
     * PEM keys stored in Secrets Manager sometimes contain literal newlines inside JSON strings.
     * This escapes them so Jackson can parse the JSON. Already-escaped \n sequences are left as-is.
     */
    private String escapePemNewlines(String raw) {
        StringBuilder out = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) {
                inString = !inString;
                out.append(c);
            } else if (inString && c == '\n') {
                out.append("\\n");
            } else if (inString && c == '\r') {
                out.append("\\r");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public String get(String secretName, String key) {
        Map<String, String> secret = cache.computeIfAbsent(secretName, this::loadAndReturn);
        String value = secret.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Key '" + key + "' not found in secret: " + secretName);
        }
        return value;
    }

    public String getJwtPrivateKey() {
        return get(jwtSecretName, "private_key");
    }

    public String getJwtPublicKey() {
        return get(jwtSecretName, "public_key");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadAndReturn(String secretName) {
        try {
            String raw = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()
            ).secretString();
            return objectMapper.readValue(escapePemNewlines(raw), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load secret: " + secretName, e);
        }
    }
}
