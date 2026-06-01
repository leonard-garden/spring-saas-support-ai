package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.chatbot.Chatbot;
import com.leonardtrinh.supportsaas.chatbot.ChatbotService;
import com.leonardtrinh.supportsaas.chatbot.ChatbotResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/widget")
public class WidgetController {

    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);

    private final ChatbotService chatbotService;
    private final ChatService chatService;

    public WidgetController(ChatbotService chatbotService, ChatService chatService) {
        this.chatbotService = chatbotService;
        this.chatService = chatService;
    }

    @Operation(summary = "Get public widget config for chatbot")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Config returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found or inactive")
    })
    @GetMapping("/{chatbotId}/config")
    public ResponseEntity<ApiResponse<ChatbotResponse>> getConfig(@PathVariable UUID chatbotId) {
        // findActiveChatbot throws 404 if not found or inactive — no tenant context needed for read
        Chatbot chatbot = chatbotService.findActiveChatbot(chatbotId);
        // Set tenant context for the downstream service call
        TenantContext.setTenantId(chatbot.getBusinessId());
        try {
            ChatbotResponse response = chatbotService.getById(chatbot.getId());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } finally {
            TenantContext.clear();
        }
    }

    @Operation(summary = "Public widget chat — streams SSE response (no auth)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE stream"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Blank or too-long query"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found or inactive"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit or quota exceeded")
    })
    @PostMapping(value = "/{chatbotId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @PathVariable UUID chatbotId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        // Resolve tenant from DB — NEVER from user input
        Chatbot chatbot = chatbotService.findActiveChatbot(chatbotId);
        UUID tenantId = chatbot.getBusinessId();
        TenantContext.setTenantId(tenantId);  // set on HTTP thread
        try {
            Conversation conversation = chatService.findOrCreateWidgetConversation(chatbotId, sessionId);
            UUID conversationId = conversation.getId();

            SseEmitter emitter = new SseEmitter(0L);
            AtomicReference<UUID> assistantMessageId = new AtomicReference<>();

            // Async Reactor callbacks run on Reactor threads — they must NOT rely on TenantContext
            // from the HTTP thread (ThreadLocal is not propagated). ChatServiceImpl.streamMessage
            // handles its own thread context internally via TransactionTemplate.
            chatService.streamMessage(conversationId, request.query(), assistantMessageId)
                    .subscribe(
                        token -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data("{\"token\":\"" + escapeJson(token) + "\"}"));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                log.warn("widget_stream_error chatbotId={} sessionId={} error={}",
                                        chatbotId, sessionId, error.getMessage());
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data("{\"error\":\"STREAM_ERROR\",\"message\":\"" +
                                              escapeJson(error.getMessage()) + "\"}"));
                            } catch (IOException ioEx) {
                                log.error("failed to send widget error event", ioEx);
                            } finally {
                                emitter.complete();
                            }
                        },
                        () -> {
                            try {
                                UUID msgId = assistantMessageId.get();
                                String msgIdStr = msgId != null ? msgId.toString() : "";
                                emitter.send(SseEmitter.event()
                                        .data("{\"done\":true,\"messageId\":\"" + msgIdStr + "\"}"));
                            } catch (IOException e) {
                                log.error("failed to send widget done event", e);
                            } finally {
                                emitter.complete();
                            }
                        }
                    );

            return emitter;
        } finally {
            // ALWAYS clear the HTTP thread — even if setup throws or stream subscription fails.
            // Async callbacks run on Reactor threads and must not call TenantContext.clear() here.
            TenantContext.clear();
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
