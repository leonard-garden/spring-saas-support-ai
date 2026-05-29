package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.chatbot.Chatbot;
import com.leonardtrinh.supportsaas.chatbot.ChatbotRepository;
import com.leonardtrinh.supportsaas.chatbot.ChatbotNotFoundException;
import com.leonardtrinh.supportsaas.document.search.HybridSearchService;
import com.leonardtrinh.supportsaas.document.search.SearchResult;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private static final int RAG_TOP_K = 5;
    private static final DateTimeFormatter YEAR_MONTH_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatbotRepository chatbotRepository;
    private final HybridSearchService hybridSearchService;
    private final MessageUsageService messageUsageService;
    private final ChatClient chatClient;
    private final TransactionTemplate requiresNewTx;

    public ChatServiceImpl(
            ConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            ChatbotRepository chatbotRepository,
            HybridSearchService hybridSearchService,
            MessageUsageService messageUsageService,
            ChatClient chatClient,
            PlatformTransactionManager txManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatbotRepository = chatbotRepository;
        this.hybridSearchService = hybridSearchService;
        this.messageUsageService = messageUsageService;
        this.chatClient = chatClient;
        this.requiresNewTx = new TransactionTemplate(txManager);
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(UUID chatbotId) {
        UUID tenantId = TenantContext.getTenantId();
        Chatbot chatbot = chatbotRepository.findById(chatbotId)
                .orElseThrow(() -> new ChatbotNotFoundException(chatbotId));

        Conversation conversation = new Conversation();
        conversation.setBusinessId(tenantId);
        conversation.setChatbotId(chatbot.getId());

        Conversation saved = conversationRepository.save(conversation);
        return new ConversationResponse(saved.getId(), saved.getChatbotId(), saved.getCreatedAt());
    }

    @Override
    public Page<ConversationSummary> listConversations(Pageable pageable) {
        Page<Conversation> page = conversationRepository.findAll(pageable);

        List<ConversationSummary> summaries = page.getContent().stream()
                .map(conv -> {
                    List<ChatMessage> messages = messageRepository
                            .findByConversationIdOrderByCreatedAtAsc(conv.getId());
                    int messageCount = messages.size();
                    Instant lastMessageAt = messages.isEmpty()
                            ? conv.getCreatedAt()
                            : messages.get(messages.size() - 1).getCreatedAt();
                    String chatbotName = chatbotRepository.findById(conv.getChatbotId())
                            .map(Chatbot::getName)
                            .orElse("Unknown");
                    return new ConversationSummary(
                        conv.getId(),
                        conv.getChatbotId(),
                        chatbotName,
                        messageCount,
                        lastMessageAt,
                        conv.getCreatedAt()
                    );
                })
                .toList();

        return new PageImpl<>(summaries, pageable, page.getTotalElements());
    }

    @Override
    public List<ChatMessageResponse> getMessages(UUID conversationId) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @Override
    public Flux<String> streamMessage(UUID conversationId, String query, AtomicReference<UUID> assistantMessageIdRef) {
        UUID tenantId = TenantContext.getTenantId();
        String yearMonth = YEAR_MONTH_FMT.format(Instant.now());

        // 1. Quota check — throws QuotaExceededException before any DB write
        messageUsageService.checkQuota(tenantId, yearMonth);

        // 2. Verify conversation exists
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        // 3. Save USER message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setBusinessId(tenantId);
        userMessage.setConversationId(conversationId);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(query);
        messageRepository.save(userMessage);

        // 4. RAG retrieval
        List<SearchResult> chunks = hybridSearchService.search(query, RAG_TOP_K);

        // 5. Load conversation history (last 20, DESC, then reverse for chronological order)
        List<ChatMessage> historyDesc = messageRepository
                .findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        List<ChatMessage> history = historyDesc.reversed();

        // 6. Build prompt
        String prompt = buildPrompt(query, chunks, history);

        // 7. Stream from LLM — accumulate tokens for persistence after completion
        // tenantId captured here on the HTTP thread; Reactor completion callback runs on a
        // different thread where TenantContext (ThreadLocal) would otherwise be empty.
        AtomicReference<StringBuilder> accumulator = new AtomicReference<>(new StringBuilder());
        UUID capturedTenantId = tenantId;

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(token -> accumulator.get().append(token))
                .doOnComplete(() -> {
                    // TransactionTemplate avoids self-invocation proxy bypass;
                    // explicit TenantContext set/clear ensures Hibernate filter works on this thread.
                    requiresNewTx.execute(status -> {
                        TenantContext.setTenantId(capturedTenantId);
                        try {
                            ChatMessage msg = new ChatMessage();
                            msg.setBusinessId(capturedTenantId);
                            msg.setConversationId(conversationId);
                            msg.setRole(MessageRole.ASSISTANT);
                            msg.setContent(accumulator.get().toString());
                            ChatMessage saved = messageRepository.save(msg);
                            assistantMessageIdRef.set(saved.getId());
                            messageUsageService.increment(capturedTenantId, yearMonth);
                            return saved;
                        } finally {
                            TenantContext.clear();
                        }
                    });
                })
                .doOnError(ex -> {
                    // Log but don't persist incomplete message — caller sends SSE error event
                });
    }

    @Override
    @Transactional
    public Conversation findOrCreateWidgetConversation(UUID chatbotId, String sessionId) {
        UUID tenantId = TenantContext.getTenantId();

        if (sessionId != null && !sessionId.isBlank()) {
            return conversationRepository
                    .findByChatbotIdAndSessionId(chatbotId, sessionId)
                    .orElseGet(() -> createWidgetConversation(tenantId, chatbotId, sessionId));
        }
        return createWidgetConversation(tenantId, chatbotId, sessionId);
    }

    // --- private helpers ---

    private Conversation createWidgetConversation(UUID tenantId, UUID chatbotId, String sessionId) {
        Conversation conversation = new Conversation();
        conversation.setBusinessId(tenantId);
        conversation.setChatbotId(chatbotId);
        conversation.setSessionId(sessionId);
        return conversationRepository.save(conversation);
    }

    private String buildPrompt(String query, List<SearchResult> chunks, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful customer support assistant. ");
        sb.append("Answer the user's question based on the provided knowledge base context. ");
        sb.append("If the answer is not in the context, say so honestly.\n\n");

        if (!chunks.isEmpty()) {
            sb.append("=== Knowledge Base Context ===\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(chunks.get(i).content()).append("\n");
            }
            sb.append("\n");
        }

        if (!history.isEmpty()) {
            sb.append("=== Conversation History ===\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getRole().name()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== User Question ===\n");
        sb.append(query);

        return sb.toString();
    }
}
