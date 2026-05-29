package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.chatbot.Chatbot;
import com.leonardtrinh.supportsaas.chatbot.ChatbotRepository;
import com.leonardtrinh.supportsaas.document.search.HybridSearchService;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private MessageUsageService messageUsageService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamSpec;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private TransactionStatus txStatus;

    private ChatServiceImpl chatService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CHATBOT_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-abc-123";

    @BeforeEach
    void setUp() {
        // Lenient: only some tests reach the TransactionTemplate callback
        lenient().when(txManager.getTransaction(any())).thenReturn(txStatus);
        chatService = new ChatServiceImpl(
            conversationRepository,
            messageRepository,
            chatbotRepository,
            hybridSearchService,
            messageUsageService,
            chatClient,
            txManager
        );
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("streamMessage — quota exceeded throws before any DB write")
    void streamMessage_quotaExceeded() {
        // given
        String yearMonth = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM")
            .withZone(java.time.ZoneOffset.UTC)
            .format(java.time.Instant.now());

        doThrow(new QuotaExceededException("messages_per_month", 100, 100))
            .when(messageUsageService).checkQuota(eq(TENANT_ID), anyString());

        // when/then
        assertThatThrownBy(() -> chatService.streamMessage(CONVERSATION_ID, "hello", new AtomicReference<>()))
            .isInstanceOf(QuotaExceededException.class);

        // no message saved
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("streamMessage — saves USER message before streaming")
    void streamMessage_savesUserMessage() {
        // given
        Conversation conv = buildConversation();
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        doNothing().when(messageUsageService).checkQuota(any(), any());
        when(hybridSearchService.search(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(CONVERSATION_ID))
            .thenReturn(Collections.emptyList());

        // mock ChatClient fluent API chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Hello", " world"));

        ChatMessage savedMsg = new ChatMessage();
        savedMsg.setBusinessId(TENANT_ID);
        savedMsg.setConversationId(CONVERSATION_ID);
        savedMsg.setRole(MessageRole.USER);
        savedMsg.setContent("What is refund policy?");
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);

        // when
        Flux<String> flux = chatService.streamMessage(CONVERSATION_ID, "What is refund policy?", new AtomicReference<>());

        // consume stream to trigger doOnNext/doOnComplete
        StepVerifier.create(flux)
            .expectNext("Hello")
            .expectNext(" world")
            .verifyComplete();

        // then — USER message saved with correct role
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, atLeastOnce()).save(captor.capture());
        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved).anyMatch(m -> m.getRole() == MessageRole.USER
                && "What is refund policy?".equals(m.getContent()));
    }

    @Test
    @DisplayName("findOrCreateWidgetConversation — no existing session creates new Conversation")
    void findOrCreateWidgetConversation_createsNew() {
        // given — no existing conversation for this session
        when(conversationRepository.findByChatbotIdAndSessionId(CHATBOT_ID, SESSION_ID))
            .thenReturn(Optional.empty());

        Conversation newConv = buildConversation();
        newConv.setSessionId(SESSION_ID);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(newConv);

        // when
        Conversation result = chatService.findOrCreateWidgetConversation(CHATBOT_ID, SESSION_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    @DisplayName("findOrCreateWidgetConversation — existing session returns existing Conversation")
    void findOrCreateWidgetConversation_resumesExisting() {
        // given — existing conversation found
        Conversation existing = buildConversation();
        existing.setSessionId(SESSION_ID);
        when(conversationRepository.findByChatbotIdAndSessionId(CHATBOT_ID, SESSION_ID))
            .thenReturn(Optional.of(existing));

        // when
        Conversation result = chatService.findOrCreateWidgetConversation(CHATBOT_ID, SESSION_ID);

        // then
        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    // --- helpers ---

    private Conversation buildConversation() {
        Conversation conv = new Conversation();
        conv.setBusinessId(TENANT_ID);
        conv.setChatbotId(CHATBOT_ID);
        return conv;
    }
}
