package com.leonardtrinh.supportsaas.document.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query(value = """
            SELECT dc.id::text, dc.document_id::text, dc.chunk_index, dc.content,
                   (1 - (dc.embedding <=> CAST(:embedding AS vector)))::float AS score,
                   d.filename AS document_name
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.business_id = CAST(:businessId AS uuid)
            ORDER BY dc.embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> vectorSearch(
            @Param("embedding") String embedding,
            @Param("businessId") String businessId,
            @Param("topK") int topK
    );

    @Query(value = """
            SELECT dc.id::text, dc.document_id::text, dc.chunk_index, dc.content,
                   ts_rank(dc.tsv, plainto_tsquery('english', :query))::float AS score,
                   d.filename AS document_name
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.business_id = CAST(:businessId AS uuid)
              AND dc.tsv @@ plainto_tsquery('english', :query)
            ORDER BY score DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> ftsSearch(
            @Param("query") String query,
            @Param("businessId") String businessId,
            @Param("topK") int topK
    );
}
