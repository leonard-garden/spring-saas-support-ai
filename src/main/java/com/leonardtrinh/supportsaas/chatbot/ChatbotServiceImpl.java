package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseNotFoundException;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChatbotServiceImpl implements ChatbotService {

    private static final String DEFAULT_COLOR = "#3B82F6";

    private final ChatbotRepository chatbotRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final String appBaseUrl;

    public ChatbotServiceImpl(
            ChatbotRepository chatbotRepository,
            KnowledgeBaseRepository kbRepository,
            @Value("${app.base-url}") String appBaseUrl) {
        this.chatbotRepository = chatbotRepository;
        this.kbRepository = kbRepository;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    @Transactional
    public ChatbotResponse create(CreateChatbotRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        validateKb(request.kbId());

        Chatbot chatbot = new Chatbot();
        chatbot.setBusinessId(tenantId);
        chatbot.setKbId(request.kbId());
        chatbot.setName(request.name());
        chatbot.setWelcomeMessage(request.welcomeMessage());
        chatbot.setPrimaryColor(
            request.primaryColor() != null ? request.primaryColor() : DEFAULT_COLOR);

        Chatbot saved = chatbotRepository.save(chatbot);
        return toResponse(saved);
    }

    @Override
    public List<ChatbotResponse> list() {
        return chatbotRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ChatbotResponse getById(UUID id) {
        return chatbotRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ChatbotNotFoundException(id));
    }

    @Override
    @Transactional
    public ChatbotResponse update(UUID id, UpdateChatbotRequest request) {
        Chatbot chatbot = chatbotRepository.findById(id)
                .orElseThrow(() -> new ChatbotNotFoundException(id));

        if (request.name() != null) {
            chatbot.setName(request.name());
        }
        if (request.welcomeMessage() != null) {
            chatbot.setWelcomeMessage(request.welcomeMessage());
        }
        if (request.primaryColor() != null) {
            chatbot.setPrimaryColor(request.primaryColor());
        }
        if (request.kbId() != null) {
            validateKb(request.kbId());
            chatbot.setKbId(request.kbId());
        }
        if (request.isActive() != null) {
            chatbot.setActive(request.isActive());
        }
        chatbot.setUpdatedAt(Instant.now());

        return toResponse(chatbotRepository.save(chatbot));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Chatbot chatbot = chatbotRepository.findById(id)
                .orElseThrow(() -> new ChatbotNotFoundException(id));
        chatbotRepository.delete(chatbot);
    }

    @Override
    public EmbedResponse getEmbedSnippet(UUID id) {
        Chatbot chatbot = chatbotRepository.findById(id)
                .orElseThrow(() -> new ChatbotNotFoundException(id));
        String widgetUrl = appBaseUrl + "/widget.js";
        String snippet = buildSnippet(chatbot.getId(), widgetUrl);
        return new EmbedResponse(snippet, widgetUrl, chatbot.getId());
    }

    @Override
    public Chatbot findActiveChatbot(UUID chatbotId) {
        return chatbotRepository.findByIdAndIsActiveTrue(chatbotId)
                .orElseThrow(ChatbotNotFoundException::new);
    }

    // --- private helpers ---

    private void validateKb(UUID kbId) {
        // Tenant filter is applied automatically via Hibernate filter in TenantFilterAspect;
        // if found, it already belongs to the current tenant.
        if (!kbRepository.existsById(kbId)) {
            throw new KnowledgeBaseNotFoundException();
        }
    }

    private ChatbotResponse toResponse(Chatbot chatbot) {
        String widgetUrl = appBaseUrl + "/widget.js";
        String snippet = buildSnippet(chatbot.getId(), widgetUrl);
        return new ChatbotResponse(
            chatbot.getId(),
            chatbot.getName(),
            chatbot.getWelcomeMessage(),
            chatbot.getPrimaryColor(),
            chatbot.getKbId(),
            chatbot.isActive(),
            snippet,
            chatbot.getCreatedAt()
        );
    }

    private String buildSnippet(UUID chatbotId, String widgetUrl) {
        return "<script src=\"" + widgetUrl + "\" data-chatbot-id=\"" + chatbotId + "\"></script>";
    }
}
