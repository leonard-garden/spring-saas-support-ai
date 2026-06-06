package com.leonardtrinh.supportsaas.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByKnowledgeBaseIdOrderByCreatedAtDesc(UUID knowledgeBaseId);
    List<Document> findAllByKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(UUID knowledgeBaseId, DocumentStatus status);
    long countByKnowledgeBaseId(UUID knowledgeBaseId);
    long countByKnowledgeBaseIdAndStatus(UUID knowledgeBaseId, DocumentStatus status);
    Optional<Document> findByIdAndBusinessId(UUID id, UUID businessId);
    boolean existsByStatus(DocumentStatus status);

    @Query(value = "SELECT COUNT(*) FROM documents WHERE business_id = :businessId", nativeQuery = true)
    long countByBusinessId(@Param("businessId") UUID businessId);
}
