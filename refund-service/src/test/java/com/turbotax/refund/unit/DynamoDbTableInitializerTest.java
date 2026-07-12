package com.turbotax.refund.unit;

import com.turbotax.refund.config.DynamoDbTableInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbTableInitializerTest {

    @Mock DynamoDbClient dynamoDbClient;

    DynamoDbTableInitializer initializer;

    @BeforeEach
    void setup() {
        initializer = new DynamoDbTableInitializer(dynamoDbClient);
        ReflectionTestUtils.setField(initializer, "filingsTable", "turbotax-filings");
    }

    @Test
    void createTablesIfNotExist_shouldSkipCreation_whenTableAlreadyExists() {
        when(dynamoDbClient.describeTable(any(java.util.function.Consumer.class)))
            .thenReturn(null);

        initializer.createTablesIfNotExist();

        verify(dynamoDbClient, never()).createTable(any(CreateTableRequest.class));
    }

    @Test
    void createTablesIfNotExist_shouldCreateTable_whenNotFound() {
        DynamoDbWaiter waiter = mock(DynamoDbWaiter.class);
        when(dynamoDbClient.describeTable(any(java.util.function.Consumer.class)))
            .thenThrow(ResourceNotFoundException.builder().message("not found").build());
        when(dynamoDbClient.waiter()).thenReturn(waiter);
        when(waiter.waitUntilTableExists(any(java.util.function.Consumer.class))).thenReturn(null);

        initializer.createTablesIfNotExist();

        verify(dynamoDbClient).createTable(any(CreateTableRequest.class));
        verify(waiter).waitUntilTableExists(any(java.util.function.Consumer.class));
    }
}
