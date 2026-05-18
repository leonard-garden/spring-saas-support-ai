package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

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

    public UUID store(String content, String contentHash, UUID documentId, UUID businessId, int chunkIndex) {
        UUID currentTenant = TenantContext.getTenantId();
        if (!businessId.equals(currentTenant)) {
            throw new IllegalStateException("businessId mismatch: expected " + currentTenant + " but got " + businessId);
        }
        float[] vector = embeddingModel.embed(content);
        String vectorString = toVectorString(vector);
        UUID chunkId = UUID.randomUUID();
        chunkRepository.insertChunk(
                chunkId.toString(),
                businessId.toString(),
                documentId.toString(),
                chunkIndex,
                content,
                contentHash,
                vectorString
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
