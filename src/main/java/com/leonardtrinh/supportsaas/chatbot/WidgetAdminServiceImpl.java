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
public class WidgetAdminServiceImpl implements WidgetAdminService {

    private static final String DEFAULT_NAME = "Support Bot";
    private static final String DEFAULT_WELCOME = "Hello! How can I help you?";
    private static final String DEFAULT_COLOR = "#3B82F6";

    private final ChatbotRepository chatbotRepository;
    private final ChatbotKnowledgeBaseRepository chatbotKbRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final String appBaseUrl;

    public WidgetAdminServiceImpl(
            ChatbotRepository chatbotRepository,
            ChatbotKnowledgeBaseRepository chatbotKbRepository,
            KnowledgeBaseRepository kbRepository,
            @Value("${app.base-url}") String appBaseUrl) {
        this.chatbotRepository = chatbotRepository;
        this.chatbotKbRepository = chatbotKbRepository;
        this.kbRepository = kbRepository;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public WidgetResponse getWidget() {
        Chatbot chatbot = chatbotRepository.findAll().stream()
                .findFirst()
                .orElseThrow(ChatbotNotFoundException::new);
        return toWidgetResponse(chatbot);
    }

    @Override
    @Transactional
    public WidgetResponse createWidget() {
        if (!chatbotRepository.findAll().isEmpty()) {
            throw new WidgetAlreadyExistsException();
        }
        UUID tenantId = TenantContext.getTenantId();

        UUID defaultKbId = kbRepository.findAll().stream()
                .findFirst()
                .map(kb -> kb.getId())
                .orElseThrow(() -> new KnowledgeBaseNotFoundException());

        Chatbot chatbot = new Chatbot();
        chatbot.setBusinessId(tenantId);
        chatbot.setName(DEFAULT_NAME);
        chatbot.setWelcomeMessage(DEFAULT_WELCOME);
        chatbot.setPrimaryColor(DEFAULT_COLOR);
        chatbot.setKbId(defaultKbId);

        Chatbot saved = chatbotRepository.save(chatbot);

        ChatbotKnowledgeBase link = new ChatbotKnowledgeBase();
        link.setBusinessId(tenantId);
        link.setChatbotId(saved.getId());
        link.setKbId(defaultKbId);
        chatbotKbRepository.save(link);

        return toWidgetResponse(saved);
    }

    @Override
    @Transactional
    public WidgetResponse updateConfig(UUID id, UpdateWidgetConfigRequest request) {
        Chatbot chatbot = chatbotRepository.findById(id)
                .orElseThrow(() -> new ChatbotNotFoundException(id));
        if (request.name() != null) chatbot.setName(request.name());
        if (request.welcomeMessage() != null) chatbot.setWelcomeMessage(request.welcomeMessage());
        if (request.primaryColor() != null) chatbot.setPrimaryColor(request.primaryColor());
        chatbot.setUpdatedAt(Instant.now());
        return toWidgetResponse(chatbotRepository.save(chatbot));
    }

    @Override
    @Transactional
    public WidgetResponse replaceKnowledgeBases(UUID id, ReplaceKnowledgeBasesRequest request) {
        Chatbot chatbot = chatbotRepository.findById(id)
                .orElseThrow(() -> new ChatbotNotFoundException(id));
        UUID tenantId = TenantContext.getTenantId();

        for (UUID kbId : request.kbIds()) {
            if (!kbRepository.existsById(kbId)) {
                throw new KnowledgeBaseNotFoundException();
            }
        }

        chatbotKbRepository.deleteAllByChatbotAndBusiness(id, tenantId);

        for (UUID kbId : request.kbIds()) {
            chatbotKbRepository.insertKb(id, kbId, tenantId);
        }

        if (!request.kbIds().isEmpty()) {
            chatbot.setKbId(request.kbIds().get(0));
            chatbot.setUpdatedAt(Instant.now());
            chatbotRepository.save(chatbot);
        }

        return toWidgetResponse(chatbot);
    }

    private WidgetResponse toWidgetResponse(Chatbot chatbot) {
        List<UUID> kbIds = chatbotKbRepository.findAllByChatbotId(chatbot.getId())
                .stream().map(ChatbotKnowledgeBase::getKbId).toList();
        String snippet = "<script src=\"" + appBaseUrl + "/widget.js\" data-widget-id=\"" + chatbot.getId() + "\"></script>";
        return new WidgetResponse(
                chatbot.getId(),
                chatbot.getName(),
                chatbot.getWelcomeMessage(),
                chatbot.getPrimaryColor(),
                chatbot.isActive(),
                kbIds,
                snippet,
                chatbot.getCreatedAt()
        );
    }
}
