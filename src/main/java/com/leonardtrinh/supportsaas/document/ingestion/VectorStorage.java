package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;
import java.util.UUID;

// Directive: Chunk writes bypass JPA intentionally — pgvector requires ?::vector cast not
// supported by Hibernate. businessId is validated against TenantContext before INSERT.
@Component
public class VectorStorage {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public VectorStorage(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID store(String content, String contentHash, UUID documentId, UUID businessId, int chunkIndex) {
        UUID currentTenant = TenantContext.getTenantId();
        if (!businessId.equals(currentTenant)) {
            throw new IllegalStateException("businessId mismatch: expected " + currentTenant + " but got " + businessId);
        }
        float[] vector = embeddingModel.embed(content);
        String pgVector = toVectorString(vector);
        UUID chunkId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO document_chunks (id, business_id, document_id, chunk_index, content, content_hash, embedding) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?::vector)",
                chunkId.toString(),
                businessId.toString(),
                documentId.toString(),
                chunkIndex,
                content,
                contentHash,
                pgVector
        );
        return chunkId;
    }

    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(Float.toString(v));
        }
        return joiner.toString();
    }
}
