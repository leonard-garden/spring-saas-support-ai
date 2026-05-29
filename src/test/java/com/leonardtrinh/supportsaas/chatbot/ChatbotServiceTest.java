package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseNotFoundException;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private KnowledgeBaseRepository kbRepository;

    private ChatbotServiceImpl chatbotService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KB_ID = UUID.randomUUID();
    private static final String APP_BASE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotServiceImpl(chatbotRepository, kbRepository, APP_BASE_URL);
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create — valid request with existing KB returns ChatbotResponse")
    void create_success() {
        // given
        CreateChatbotRequest request = new CreateChatbotRequest(
            "Test Bot", "Hello!", "#3B82F6", KB_ID);

        when(kbRepository.existsById(KB_ID)).thenReturn(true);

        Chatbot savedChatbot = new Chatbot();
        savedChatbot.setBusinessId(TENANT_ID);
        savedChatbot.setKbId(KB_ID);
        savedChatbot.setName("Test Bot");
        savedChatbot.setWelcomeMessage("Hello!");
        savedChatbot.setPrimaryColor("#3B82F6");
        when(chatbotRepository.save(any(Chatbot.class))).thenReturn(savedChatbot);

        // when
        ChatbotResponse response = chatbotService.create(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Bot");
        assertThat(response.welcomeMessage()).isEqualTo("Hello!");
        assertThat(response.kbId()).isEqualTo(KB_ID);
        verify(chatbotRepository).save(any(Chatbot.class));
    }

    @Test
    @DisplayName("create — kbId not found throws KnowledgeBaseNotFoundException")
    void create_kbNotFound() {
        // given
        UUID unknownKbId = UUID.randomUUID();
        CreateChatbotRequest request = new CreateChatbotRequest(
            "Bot", "Hi", null, unknownKbId);

        when(kbRepository.existsById(unknownKbId)).thenReturn(false);

        // when/then
        assertThatThrownBy(() -> chatbotService.create(request))
            .isInstanceOf(KnowledgeBaseNotFoundException.class);

        verify(chatbotRepository, never()).save(any());
    }

    @Test
    @DisplayName("findActiveChatbot — inactive chatbot throws ChatbotNotFoundException")
    void findActiveChatbot_inactive() {
        // given
        UUID chatbotId = UUID.randomUUID();
        when(chatbotRepository.findByIdAndIsActiveTrue(chatbotId)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> chatbotService.findActiveChatbot(chatbotId))
            .isInstanceOf(ChatbotNotFoundException.class);
    }

    @Test
    @DisplayName("findActiveChatbot — active chatbot returns Chatbot entity")
    void findActiveChatbot_active() {
        // given
        UUID chatbotId = UUID.randomUUID();
        Chatbot chatbot = new Chatbot();
        chatbot.setBusinessId(TENANT_ID);
        chatbot.setName("Active Bot");
        when(chatbotRepository.findByIdAndIsActiveTrue(chatbotId)).thenReturn(Optional.of(chatbot));

        // when
        Chatbot result = chatbotService.findActiveChatbot(chatbotId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Active Bot");
    }
}
