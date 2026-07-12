package com.turbotax.refund.unit;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import com.turbotax.refund.dynamodb.FilingDynamoRepository;
import com.turbotax.refund.dynamodb.FilingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilingDynamoRepositoryTest {

    @Mock DynamoDbEnhancedClient enhancedClient;
    @Mock DynamoDbTable<FilingItem> table;

    FilingDynamoRepository repository;

    @BeforeEach
    void setup() {
        repository = new FilingDynamoRepository(enhancedClient, "turbotax-filings");
        when(enhancedClient.table(eq("turbotax-filings"), any(TableSchema.class))).thenReturn(table);
    }

    private FilingItem item(String taxpayerId, String sk, IrsStatus status) {
        return FilingItem.builder().taxpayerId(taxpayerId).sk(sk).irsStatus(status.name()).build();
    }

    /** SdkIterable's only abstract method is iterator() (inherited from Iterable), so it's a
     *  valid lambda target -- a real implementation here, not a mock, so its default methods
     *  (.stream(), .spliterator()) run for real instead of returning Mockito's empty default. */
    private SdkIterable<FilingItem> itemsOf(List<FilingItem> items) {
        return items::iterator;
    }

    @Test
    void save_shouldPutItemAndReturnIt() {
        var item = item("tp-1", "2024#F1040#FEDERAL", IrsStatus.RECEIVED);

        var result = repository.save(item);

        assertThat(result).isSameAs(item);
        org.mockito.Mockito.verify(table).putItem(item);
    }

    @Test
    void findById_shouldReturnItem_whenPresent() {
        var item = item("tp-1", "2024#F1040#FEDERAL", IrsStatus.RECEIVED);
        when(table.getItem(any(Key.class))).thenReturn(item);

        var result = repository.findById("tp-1", "2024#F1040#FEDERAL");

        assertThat(result).contains(item);
    }

    @Test
    void findById_shouldReturnEmpty_whenMissing() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        assertThat(repository.findById("tp-1", "missing")).isEmpty();
    }

    @Test
    void findFiling_shouldBuildSkAndDelegateToFindById() {
        var item = item("tp-1", "2024#F1040#FEDERAL", IrsStatus.RECEIVED);
        when(table.getItem(any(Key.class))).thenReturn(item);

        var result = repository.findFiling("tp-1", "2024", FormType.F1040, "federal");

        assertThat(result).contains(item);
    }

    @Test
    void findAllByTaxpayerId_shouldReturnAllItemsForPartition() {
        var items = List.of(
            item("tp-1", "2023#F1040#FEDERAL", IrsStatus.DEPOSITED),
            item("tp-1", "2024#F1040#FEDERAL", IrsStatus.RECEIVED)
        );
        PageIterable<FilingItem> pages = mock(PageIterable.class);
        var sdkIterable = itemsOf(items);
        when(table.query(any(QueryConditional.class))).thenReturn(pages);
        when(pages.items()).thenReturn(sdkIterable);

        var result = repository.findAllByTaxpayerId("tp-1");

        assertThat(result).containsExactlyElementsOf(items);
    }

    @Test
    void findAllPending_shouldExcludeDepositedFilings() {
        var pending = item("tp-1", "2024#F1040#FEDERAL", IrsStatus.RECEIVED);
        var deposited = item("tp-1", "2023#F1040#FEDERAL", IrsStatus.DEPOSITED);
        PageIterable<FilingItem> pages = mock(PageIterable.class);
        var sdkIterable = itemsOf(List.of(pending, deposited));
        when(table.scan()).thenReturn(pages);
        when(pages.items()).thenReturn(sdkIterable);

        var result = repository.findAllPending();

        assertThat(result).containsExactly(pending);
    }
}
