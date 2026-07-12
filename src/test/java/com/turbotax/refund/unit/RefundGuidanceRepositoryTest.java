package com.turbotax.refund.unit;

import com.turbotax.refund.guidance.GuidanceDoc;
import com.turbotax.refund.guidance.RefundGuidanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundGuidanceRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;

    RefundGuidanceRepository repository;

    @BeforeEach
    void setup() {
        repository = new RefundGuidanceRepository(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findTopDocIds_shouldReturnMappedList_whenSituationExists() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Array sqlArray = mock(Array.class);
        when(sqlArray.getArray()).thenReturn(new Long[]{3L, 1L, 2L});
        when(rs.getArray("top_doc_ids")).thenReturn(sqlArray);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(inv -> {
                RowMapper<List<Long>> mapper = inv.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });

        var result = repository.findTopDocIds("FLAGGED_INDIVIDUAL_FEDERAL");

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(3L, 1L, 2L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findTopDocIds_shouldHandleNullArray() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getArray("top_doc_ids")).thenReturn(null);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(inv -> {
                RowMapper<List<Long>> mapper = inv.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });

        var result = repository.findTopDocIds("SOME_KEY");

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void findTopDocIds_shouldReturnEmptyOptional_whenNoSituationMatches() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());

        assertThat(repository.findTopDocIds("MISSING_KEY")).isEmpty();
    }

    @Test
    void findDocsByIds_shouldReturnEmptyList_withoutQuerying_whenIdsEmpty() {
        var result = repository.findDocsByIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDocsByIds_shouldPreserveRequestedOrder_notSqlResultOrder() throws SQLException {
        // SQL returns docs in a different order than requested; findDocsByIds must reorder to match ids.
        GuidanceDoc doc1 = new GuidanceDoc(1L, "topic1", "content1", "https://a");
        GuidanceDoc doc2 = new GuidanceDoc(2L, "topic2", "content2", "https://b");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(doc2, doc1));

        var result = repository.findDocsByIds(List.of(1L, 2L));

        assertThat(result).containsExactly(doc1, doc2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findDocsByIds_rowMapper_shouldMapAllFields() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getString("topic")).thenReturn("cp05_notice");
        when(rs.getString("content")).thenReturn("Some content.");
        when(rs.getString("source_url")).thenReturn("https://irs.gov/cp05");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(inv -> {
                RowMapper<GuidanceDoc> mapper = inv.getArgument(1);
                return List.of(mapper.mapRow(rs, 0));
            });

        var result = repository.findDocsByIds(List.of(42L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(42L);
        assertThat(result.get(0).topic()).isEqualTo("cp05_notice");
        assertThat(result.get(0).content()).isEqualTo("Some content.");
        assertThat(result.get(0).sourceUrl()).isEqualTo("https://irs.gov/cp05");
    }
}
