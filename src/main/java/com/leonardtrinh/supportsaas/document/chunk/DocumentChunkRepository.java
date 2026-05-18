package com.leonardtrinh.supportsaas.document.chunk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);
    int countByDocumentId(UUID documentId);
}
