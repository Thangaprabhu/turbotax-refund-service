package com.turbotax.refund.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotax.refund.domain.dto.request.CreateFilingRequest;
import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.request.RegisterRequest;
import com.turbotax.refund.domain.enums.AccountType;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.domain.enums.FormType;
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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class FilingControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("turbotax_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3"))
        .withServices(KMS, SECRETSMANAGER, DYNAMODB);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.region", localstack::getRegion);
        registry.add("dynamodb.endpoint", () -> localstack.getEndpointOverride(DYNAMODB).toString());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        var register = new RegisterRequest(email, "password123", AccountType.INDIVIDUAL);
        var result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken").asText();
    }

    private String createTaxpayer(String token, String ssn) throws Exception {
        var req = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, ssn, "Test Filer", null, "TX");
        var result = mockMvc.perform(post("/api/v1/taxpayers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText();
    }

    @Test
    void shouldCreateFiling_andReturnReceivedStatus() throws Exception {
        var token = registerAndGetToken("filer1@example.com");
        var taxpayerId = createTaxpayer(token, "111-22-3333");

        var filing = new CreateFilingRequest(FormType.F1040, "2023", "FEDERAL", "2024-04-15");
        mockMvc.perform(post("/api/v1/taxpayers/{id}/filings", taxpayerId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filing)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.irsStatus").value("RECEIVED"))
            .andExpect(jsonPath("$.taxYear").value("2023"));
    }

    @Test
    void shouldReturn403_whenUserAccessesDifferentTaxpayerFilings() throws Exception {
        var token1 = registerAndGetToken("owner@example.com");
        var taxpayerId = createTaxpayer(token1, "222-33-4444");

        var token2 = registerAndGetToken("intruder@example.com");

        mockMvc.perform(get("/api/v1/taxpayers/{id}/filings", taxpayerId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isForbidden());
    }
}
