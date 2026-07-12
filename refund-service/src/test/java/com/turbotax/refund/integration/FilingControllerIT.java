package com.turbotax.refund.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.security.JwtService;
import org.junit.jupiter.api.AfterAll;
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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * refund-service no longer owns taxpayer data or the prediction engine -- it calls
 * taxpayer-service (access check) and ai-service (prediction) over HTTP. Rather than standing
 * up those services for this IT, a lightweight JDK HttpServer stands in for both, stubbing just
 * the responses this controller's flows depend on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class FilingControllerIT {

    private static final String JWT_SECRET_NAME = "dev/turbotax/jwt";
    private static final UUID OWNED_TAXPAYER_ID = UUID.randomUUID();
    private static final UUID DENIED_TAXPAYER_ID = UUID.randomUUID();

    private static HttpServer upstreamStub;

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3"))
        .withServices(SECRETSMANAGER, DYNAMODB);

    @BeforeAll
    static void startUpstreamStub() throws Exception {
        upstreamStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ObjectMapper mapper = new ObjectMapper();

        upstreamStub.createContext("/api/v1/taxpayers/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            byte[] body;
            int status;
            if (OWNED_TAXPAYER_ID.toString().equals(id)) {
                status = 200;
                body = mapper.writeValueAsBytes(java.util.Map.of(
                    "id", id, "taxpayerType", "INDIVIDUAL", "displayName", "Test Filer",
                    "entityType", "", "stateOfReg", ""));
            } else {
                status = 403;
                body = "{\"detail\":\"Access denied\"}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        upstreamStub.createContext("/api/v1/predictions", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        upstreamStub.start();
    }

    @AfterAll
    static void stopUpstreamStub() {
        upstreamStub.stop(0);
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.access-key", localstack::getAccessKey);
        registry.add("aws.secret-key", localstack::getSecretKey);
        registry.add("aws.secrets.jwt-secret-name", () -> JWT_SECRET_NAME);
        registry.add("dynamodb.endpoint", () -> localstack.getEndpointOverride(DYNAMODB).toString());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999"); // disabled for IT
        registry.add("services.taxpayer-service.base-url",
            () -> "http://localhost:" + upstreamStub.getAddress().getPort());
        registry.add("services.ai-service.base-url",
            () -> "http://localhost:" + upstreamStub.getAddress().getPort());
    }

    @BeforeAll
    static void seedJwtSecret() throws Exception {
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
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .build()) {
            String json = new ObjectMapper().writeValueAsString(
                java.util.Map.of("private_key", privatePem, "public_key", publicPem));
            client.createSecret(CreateSecretRequest.builder()
                .name(JWT_SECRET_NAME)
                .secretString(json)
                .build());
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    private String tokenFor(UUID userId) {
        return jwtService.generateToken(userId, "filer@example.com", "USER");
    }

    @Test
    void shouldCreateFiling_andReturnReceivedStatus() throws Exception {
        var token = tokenFor(UUID.randomUUID());

        var filing = new CreateFilingRequest(FormType.F1040, "2023", "FEDERAL", "2024-04-15");
        mockMvc.perform(post("/api/v1/taxpayers/{id}/filings", OWNED_TAXPAYER_ID)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filing)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.irsStatus").value("RECEIVED"))
            .andExpect(jsonPath("$.taxYear").value("2023"));
    }

    @Test
    void shouldReturn403_whenTaxpayerServiceDeniesAccess() throws Exception {
        var token = tokenFor(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/taxpayers/{id}/filings", DENIED_TAXPAYER_ID)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/taxpayers/{id}/filings", OWNED_TAXPAYER_ID))
            .andExpect(status().isUnauthorized());
    }
}
