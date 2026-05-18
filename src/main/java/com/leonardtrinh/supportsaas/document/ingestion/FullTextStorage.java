package com.leonardtrinh.supportsaas.document.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FullTextStorage {

    private final JdbcTemplate jdbcTemplate;

    public FullTextStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateTsv(UUID chunkId) {
        jdbcTemplate.update(
                "UPDATE document_chunks SET tsv = to_tsvector('english', content) WHERE id = ?::uuid",
                chunkId.toString()
        );
    }
}
