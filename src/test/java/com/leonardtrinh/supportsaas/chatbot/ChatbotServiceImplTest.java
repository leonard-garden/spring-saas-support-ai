package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private KnowledgeBaseRepository kbRepository;

    private ChatbotServiceImpl chatbotService;

    private static final UUID CHATBOT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotServiceImpl(
            chatbotRepository,
            kbRepository,
            "http://localhost:8081"
        );
    }

    @Test
    @DisplayName("findActiveChatbot — returns chatbot when found and active")
    void findActiveChatbot_active_returns() {
        Chatbot chatbot = new Chatbot();
        chatbot.setActive(true);
        when(chatbotRepository.findById(CHATBOT_ID)).thenReturn(Optional.of(chatbot));

        Chatbot result = chatbotService.findActiveChatbot(CHATBOT_ID);

        assertThat(result).isSameAs(chatbot);
    }

    @Test
    @DisplayName("findActiveChatbot — throws ChatbotNotFoundException (404) when chatbot does not exist")
    void findActiveChatbot_notFound_throws404() {
        when(chatbotRepository.findById(CHATBOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatbotService.findActiveChatbot(CHATBOT_ID))
            .isInstanceOf(ChatbotNotFoundException.class)
            .satisfies(ex -> assertThat(((ChatbotNotFoundException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("findActiveChatbot — throws ChatbotInactiveException (403) when chatbot is inactive")
    void findActiveChatbot_inactive_throws403() {
        Chatbot chatbot = new Chatbot();
        chatbot.setActive(false);
        when(chatbotRepository.findById(CHATBOT_ID)).thenReturn(Optional.of(chatbot));

        assertThatThrownBy(() -> chatbotService.findActiveChatbot(CHATBOT_ID))
            .isInstanceOf(ChatbotInactiveException.class)
            .satisfies(ex -> assertThat(((ChatbotInactiveException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
