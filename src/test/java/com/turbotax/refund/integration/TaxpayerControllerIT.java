package com.turbotax.refund.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.request.LoginRequest;
import com.turbotax.refund.domain.dto.request.RegisterRequest;
import com.turbotax.refund.domain.enums.AccountType;
import com.turbotax.refund.domain.enums.TaxpayerType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.KMS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
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

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.region", localstack::getRegion);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999"); // disabled for IT
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldRegisterAndCreateTaxpayer() throws Exception {
        // Register a user
        var register = new RegisterRequest("test@example.com", "password123", AccountType.INDIVIDUAL);
        var registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isCreated())
            .andReturn();

        var authResponse = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        var token = authResponse.get("accessToken").asText();

        // Create taxpayer
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
        var register = new RegisterRequest("test2@example.com", "password123", AccountType.INDIVIDUAL);
        var registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isCreated())
            .andReturn();

        var token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
            .get("accessToken").asText();

        var badReq = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "INVALID", "Bob", null, null);
        mockMvc.perform(post("/api/v1/taxpayers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
            .andExpect(status().isBadRequest());
    }
}
