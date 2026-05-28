package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Component
public class VectorStorage {

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;

    public VectorStorage(EmbeddingModel embeddingModel, DocumentChunkRepository chunkRepository) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
    }

    public void store(String content, String contentHash, UUID documentId, UUID businessId, int chunkIndex) {
        UUID currentTenant = TenantContext.getTenantId();
        if (!businessId.equals(currentTenant)) {
            throw new IllegalStateException("businessId mismatch: expected " + currentTenant + " but got " + businessId);
        }
        String sanitized = sanitize(content);
        float[] vector = embeddingModel.embed(sanitized);
        String vectorString = toVectorString(vector);
        chunkRepository.insertChunk(
                UUID.randomUUID().toString(),
                businessId.toString(),
                documentId.toString(),
                chunkIndex,
                sanitized,
                contentHash,
                vectorString
        );
    }

    /**
     * PostgreSQL rejects null bytes (0x00) in text columns — strip them along with
     * other non-printable control chars that PDFs sometimes embed via font metadata.
     */
    private String sanitize(String text) {
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    public List<Object[]> search(String query, UUID businessId, int topK) {
        float[] vector = embeddingModel.embed(query);
        String vectorString = toVectorString(vector);
        return chunkRepository.vectorSearch(vectorString, businessId.toString(), topK);
    }

    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(Float.toString(v));
        }
        return joiner.toString();
    }
}
