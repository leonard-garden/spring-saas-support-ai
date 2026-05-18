package com.leonardtrinh.supportsaas.document.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);
    int countByDocumentId(UUID documentId);

    @Modifying
    @Query(value = """
            INSERT INTO document_chunks (id, business_id, document_id, chunk_index, content, content_hash, embedding, tsv)
            VALUES (CAST(:id AS uuid), CAST(:businessId AS uuid), CAST(:documentId AS uuid), :chunkIndex, :content, :contentHash,
                    CAST(:embedding AS vector), to_tsvector('english', :content))
            """, nativeQuery = true)
    void insertChunk(
            @Param("id") String id,
            @Param("businessId") String businessId,
            @Param("documentId") String documentId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("contentHash") String contentHash,
            @Param("embedding") String embedding
    );

    @Modifying
    @Query(value = "DELETE FROM document_chunks WHERE document_id = CAST(:documentId AS uuid)", nativeQuery = true)
    void deleteByDocumentId(@Param("documentId") String documentId);
}
