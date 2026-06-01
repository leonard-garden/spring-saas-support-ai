package com.leonardtrinh.supportsaas.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatbotKnowledgeBaseRepository extends JpaRepository<ChatbotKnowledgeBase, UUID> {

    List<ChatbotKnowledgeBase> findAllByChatbotId(UUID chatbotId);

    @Modifying
    @Query(value = "DELETE FROM chatbot_knowledge_bases WHERE chatbot_id = CAST(:chatbotId AS uuid) AND business_id = CAST(:businessId AS uuid)", nativeQuery = true)
    void deleteAllByChatbotAndBusiness(@Param("chatbotId") UUID chatbotId, @Param("businessId") UUID businessId);

    @Modifying
    @Query(value = "INSERT INTO chatbot_knowledge_bases (id, chatbot_id, kb_id, business_id) VALUES (gen_random_uuid(), CAST(:chatbotId AS uuid), CAST(:kbId AS uuid), CAST(:businessId AS uuid))", nativeQuery = true)
    void insertKb(@Param("chatbotId") UUID chatbotId, @Param("kbId") UUID kbId, @Param("businessId") UUID businessId);
}
