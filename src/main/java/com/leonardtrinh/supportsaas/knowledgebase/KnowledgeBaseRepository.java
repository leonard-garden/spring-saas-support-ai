package com.leonardtrinh.supportsaas.knowledgebase;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    Optional<KnowledgeBase> findByBusinessId(UUID businessId);
    long countByBusinessId(UUID businessId);
}
