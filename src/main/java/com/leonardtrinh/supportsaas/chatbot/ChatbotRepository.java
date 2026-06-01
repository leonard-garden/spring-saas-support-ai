package com.leonardtrinh.supportsaas.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatbotRepository extends JpaRepository<Chatbot, UUID> {

    Optional<Chatbot> findByIdAndIsActiveTrue(UUID id);
}
