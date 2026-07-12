package com.turbotax.taxpayer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotax.taxpayer.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.taxpayer.domain.entity.User;
import com.turbotax.taxpayer.domain.enums.AccountType;
import com.turbotax.taxpayer.domain.enums.TaxpayerType;
import com.turbotax.taxpayer.repository.UserRepository;
import com.turbotax.taxpayer.security.JwtService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.KMS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

/**
 * Now that auth-service and taxpayer-service are separate deployables, this IT no longer calls
 * a register endpoint to obtain a token (taxpayer-service has none) -- it seeds a User row
 * directly and mints a JWT via taxpayer-service's own JwtService bean, the same way it would
 * validate a token that auth-service issued.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class TaxpayerControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("turbotax_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3"))
        .withServices(KMS, SECRETSMANAGER);

    private static final String JWT_SECRET_NAME = "dev/turbotax/jwt";

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.access-key", localstack::getAccessKey);
        registry.add("aws.secret-key", localstack::getSecretKey);
        registry.add("aws.secrets.jwt-secret-name", () -> JWT_SECRET_NAME);
    }

    @BeforeAll
    static void seedAwsResources() throws Exception {
        AwsCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

        try (SecretsManagerClient client = SecretsManagerClient.builder()
            .endpointOverride(localstack.getEndpoint())
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(credentials)
            .build()) {
            String json = new ObjectMapper().writeValueAsString(
                java.util.Map.of("private_key", privatePem, "public_key", publicPem));
            client.createSecret(CreateSecretRequest.builder()
                .name(JWT_SECRET_NAME)
                .secretString(json)
                .build());
        }

        try (KmsClient kms = KmsClient.builder()
            .endpointOverride(localstack.getEndpoint())
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(credentials)
            .build()) {
            String keyId = kms.createKey(CreateKeyRequest.builder().build()).keyMetadata().keyId();
            kms.createAlias(CreateAliasRequest.builder()
                .aliasName("alias/turbotax-pii")
                .targetKeyId(keyId)
                .build());
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private String createUserAndGetToken(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("irrelevant-for-this-test");
        user.setAccountType(AccountType.INDIVIDUAL);
        User saved = userRepository.save(user);
        return jwtService.generateToken(saved.getId(), email, "USER");
    }

    @Test
    void shouldRegisterAndCreateTaxpayer() throws Exception {
        var token = createUserAndGetToken("test@example.com");

        var taxpayerReq = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "123-45-6789", "Alice Smith", null, "CA");
        mockMvc.perform(post("/api/v1/taxpayers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(taxpayerReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.displayName").value("Alice Smith"))
            .andExpect(jsonPath("$.taxpayerType").value("INDIVIDUAL"));
    }

    @Test
    void shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/taxpayers"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400_whenInvalidTaxId() throws Exception {
        var token = createUserAndGetToken("test2@example.com");

        var badReq = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "INVALID", "Bob", null, null);
        mockMvc.perform(post("/api/v1/taxpayers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn403_whenUserHasNoAccessToTaxpayer() throws Exception {
        var ownerToken = createUserAndGetToken("owner@example.com");
        var createResult = mockMvc.perform(post("/api/v1/taxpayers")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "999-88-7777", "Owner Taxpayer", null, null))))
            .andExpect(status().isCreated())
            .andReturn();
        var taxpayerId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var intruderToken = createUserAndGetToken("intruder-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/v1/taxpayers/{id}", taxpayerId)
                .header("Authorization", "Bearer " + intruderToken))
            .andExpect(status().isForbidden());
    }
}
