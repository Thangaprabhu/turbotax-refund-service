package com.turbotax.refund.dynamodb;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FilingDynamoRepository {

    private final DynamoDbEnhancedClient dynamoDbClient;
    private final String tableName;

    private DynamoDbTable<FilingItem> table() {
        return dynamoDbClient.table(tableName, TableSchema.fromBean(FilingItem.class));
    }

    public FilingItem save(FilingItem item) {
        table().putItem(item);
        return item;
    }

    public Optional<FilingItem> findById(String taxpayerId, String sk) {
        Key key = Key.builder().partitionValue(taxpayerId).sortValue(sk).build();
        return Optional.ofNullable(table().getItem(key));
    }

    public Optional<FilingItem> findFiling(String taxpayerId, String taxYear,
                                            FormType formType, String jurisdiction) {
        String sk = FilingItem.buildSk(taxYear, formType, jurisdiction);
        return findById(taxpayerId, sk);
    }

    public List<FilingItem> findAllByTaxpayerId(String taxpayerId) {
        QueryConditional query = QueryConditional
            .keyEqualTo(Key.builder().partitionValue(taxpayerId).build());
        return table().query(query)
            .items()
            .stream()
            .toList();
    }

    // Returns all filings not yet DEPOSITED — used by the IRS poller
    public List<FilingItem> findAllPending() {
        return table().scan()
            .items()
            .stream()
            .filter(f -> !IrsStatus.DEPOSITED.name().equals(f.getIrsStatus()))
            .toList();
    }
}
