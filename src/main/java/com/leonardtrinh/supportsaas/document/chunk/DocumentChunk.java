package com.leonardtrinh.supportsaas.document.chunk;

import com.leonardtrinh.supportsaas.common.TenantEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }

    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public void setContent(String content) { this.content = content; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
