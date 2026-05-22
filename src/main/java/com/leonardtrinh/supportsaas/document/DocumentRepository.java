package com.leonardtrinh.supportsaas.document;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
