package com.turbotax.refund.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamoDbTableInitializer {

    private final DynamoDbClient dynamoDbClient;

    @Value("${dynamodb.table.filings:turbotax-filings}")
    private String filingsTable;

    @PostConstruct
    public void createTablesIfNotExist() {
        if (!tableExists(filingsTable)) {
            createFilingsTable();
        } else {
            log.info("DynamoDB table '{}' already exists", filingsTable);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            dynamoDbClient.describeTable(r -> r.tableName(tableName));
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void createFilingsTable() {
        log.info("Creating DynamoDB table '{}'", filingsTable);
        dynamoDbClient.createTable(CreateTableRequest.builder()
            .tableName(filingsTable)
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("taxpayerId")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("sk")
                    .attributeType(ScalarAttributeType.S)
                    .build()
            )
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("taxpayerId")
                    .keyType(KeyType.HASH)
                    .build(),
                KeySchemaElement.builder()
                    .attributeName("sk")
                    .keyType(KeyType.RANGE)
                    .build()
            )
            .build());

        waitForTable(filingsTable);
        log.info("DynamoDB table '{}' created successfully", filingsTable);
    }

    private void waitForTable(String tableName) {
        dynamoDbClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
    }
}
