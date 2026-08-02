package com.turbotax.ai.repository;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reads the RAG tables populated offline by ml/rag/build_knowledge_base.py
 * (see V6__add_refund_guidance_rag_tables.sql). Plain JDBC, not JPA -- these
 * tables aren't part of the core domain model and the pgvector column type
 * has no bearing here since retrieval already happened at ingestion time.
 */
@Repository
@RequiredArgsConstructor
public class RefundGuidanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<List<Long>> findTopDocIds(String situationKey) {
        List<List<Long>> rows = jdbcTemplate.query(
            "SELECT top_doc_ids FROM refund_guidance_situations WHERE situation_key = ?",
            (rs, rowNum) -> toLongList(rs.getArray("top_doc_ids")),
            situationKey
        );
        return rows.stream().findFirst();
    }

    /** Returned in whatever order the SQL IN clause happens to yield -- not relevance order. */
    public List<GuidanceDoc> findDocsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
            "SELECT id, topic, content, source_url, simulated_internal_content FROM refund_guidance_docs WHERE id IN (" + placeholders + ")",
            (rs, rowNum) -> new GuidanceDoc(rs.getLong("id"), rs.getString("topic"), rs.getString("content"),
                rs.getString("source_url"), rs.getBoolean("simulated_internal_content")),
            ids.toArray()
        );
    }

    private static List<Long> toLongList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Long[] boxed = (Long[]) array.getArray();
        return List.of(boxed);
    }
}
