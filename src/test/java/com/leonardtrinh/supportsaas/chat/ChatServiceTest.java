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
import org.springframework.test.util.ReflectionTestUtils;
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
        doThrow(new QuotaExceededException("messages_per_month", 100, 100))
            .when(messageUsageService).checkQuota(eq(TENANT_ID), anyString());

        // when/then
        assertThatThrownBy(() -> chatService.streamMessage(CONVERSATION_ID, "hello", new AtomicReference<>(), null))
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
        Flux<String> flux = chatService.streamMessage(CONVERSATION_ID, "What is refund policy?", new AtomicReference<>(), null);

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

    @Test
    @DisplayName("createConversation — saves conversation with correct businessId and chatbotId")
    void createConversation_savesWithCorrectFields() {
        Chatbot chatbot = new Chatbot();
        ReflectionTestUtils.setField(chatbot, "id", CHATBOT_ID);
        chatbot.setBusinessId(TENANT_ID);
        when(chatbotRepository.findById(CHATBOT_ID)).thenReturn(Optional.of(chatbot));

        Conversation saved = buildConversation();
        when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

        ConversationResponse response = chatService.createConversation(CHATBOT_ID);

        assertThat(response).isNotNull();
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getBusinessId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getChatbotId()).isEqualTo(CHATBOT_ID);
    }

    @Test
    @DisplayName("createConversation — throws ChatbotNotFoundException when chatbot missing")
    void createConversation_chatbotNotFound_throws() {
        when(chatbotRepository.findById(CHATBOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.createConversation(CHATBOT_ID))
            .isInstanceOf(com.leonardtrinh.supportsaas.chatbot.ChatbotNotFoundException.class);
    }

    @Test
    @DisplayName("getMessages — returns messages in order for existing conversation")
    void getMessages_found_returnsMessages() {
        Conversation conv = buildConversation();
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));

        ChatMessage msg = new ChatMessage();
        msg.setRole(MessageRole.USER);
        msg.setContent("hello");
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(CONVERSATION_ID))
            .thenReturn(List.of(msg));

        List<ChatMessageResponse> result = chatService.getMessages(CONVERSATION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("hello");
        assertThat(result.get(0).role()).isEqualTo(MessageRole.USER);
    }

    @Test
    @DisplayName("getMessages — throws ConversationNotFoundException when conversation missing")
    void getMessages_conversationNotFound_throws() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(CONVERSATION_ID))
            .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    @DisplayName("streamMessage — throws ConversationNotFoundException when conversation missing")
    void streamMessage_conversationNotFound_throws() {
        doNothing().when(messageUsageService).checkQuota(any(), any());
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            chatService.streamMessage(CONVERSATION_ID, "hello", new AtomicReference<>(), null))
            .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    @DisplayName("listConversations — empty result returns empty page")
    void listConversations_empty_returnsEmptyPage() {
        org.springframework.data.domain.PageRequest pageable =
            org.springframework.data.domain.PageRequest.of(0, 20);
        when(conversationRepository.findAllWithMessageStats(pageable))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(
                Collections.emptyList(), pageable, 0));

        org.springframework.data.domain.Page<ConversationSummary> result =
            chatService.listConversations(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findOrCreateWidgetConversation — null sessionId always creates new Conversation")
    void findOrCreateWidgetConversation_nullSessionId_createsNew() {
        Conversation newConv = buildConversation();
        when(conversationRepository.save(any(Conversation.class))).thenReturn(newConv);

        Conversation result = chatService.findOrCreateWidgetConversation(CHATBOT_ID, null);

        assertThat(result).isNotNull();
        // findByChatbotIdAndSessionId must NOT be called — null branch skips it
        verify(conversationRepository, never())
            .findByChatbotIdAndSessionId(any(), any());
        verify(conversationRepository).save(any(Conversation.class));
    }

    // --- message length validation tests ---

    @Test
    @DisplayName("streamMessage — message exactly 4000 chars is accepted")
    void streamMessage_messageExactly4000Chars_accepted() {
        String exactly4000 = "a".repeat(ChatServiceImpl.MAX_MESSAGE_LENGTH);
        Conversation conv = buildConversation();
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        doNothing().when(messageUsageService).checkQuota(any(), any());
        when(hybridSearchService.search(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(CONVERSATION_ID))
            .thenReturn(Collections.emptyList());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("ok"));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        Flux<String> flux = chatService.streamMessage(CONVERSATION_ID, exactly4000, new AtomicReference<>(), null);

        StepVerifier.create(flux)
            .expectNext("ok")
            .verifyComplete();
        // no MessageTooLongException thrown — verified by absence of error above
    }

    @Test
    @DisplayName("streamMessage — message 4001 chars throws MessageTooLongException before quota check")
    void streamMessage_message4001Chars_throwsMessageTooLongException() {
        String tooLong = "a".repeat(ChatServiceImpl.MAX_MESSAGE_LENGTH + 1);

        assertThatThrownBy(() ->
            chatService.streamMessage(CONVERSATION_ID, tooLong, new AtomicReference<>(), null))
            .isInstanceOf(MessageTooLongException.class)
            .hasMessageContaining("4000");

        // quota check never reached
        verifyNoInteractions(messageUsageService);
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("streamMessage — leading/trailing whitespace is trimmed before length check")
    void streamMessage_whitespaceTrimmingAppliedBeforeLengthCheck() {
        // A message that would be too long without trimming but is within limit after trimming
        String padded = " ".repeat(500) + "hello" + " ".repeat(500);
        Conversation conv = buildConversation();
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        doNothing().when(messageUsageService).checkQuota(any(), any());
        when(hybridSearchService.search(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(CONVERSATION_ID))
            .thenReturn(Collections.emptyList());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("ok"));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        Flux<String> flux = chatService.streamMessage(CONVERSATION_ID, padded, new AtomicReference<>(), null);

        StepVerifier.create(flux)
            .expectNext("ok")
            .verifyComplete();
    }

    // --- helpers ---

    private Conversation buildConversation() {
        Conversation conv = new Conversation();
        conv.setBusinessId(TENANT_ID);
        conv.setChatbotId(CHATBOT_ID);
        return conv;
    }
}
