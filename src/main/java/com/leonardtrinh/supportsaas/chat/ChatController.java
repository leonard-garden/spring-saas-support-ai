package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Create conversation")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Conversation created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found")
    })
    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = chatService.createConversation(request.chatbotId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "List conversations for tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated conversation list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PageResponse<ConversationSummary>>> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ConversationSummary> result = chatService.listConversations(pageable);
        PageResponse<ConversationSummary> response = new PageResponse<>(
            result.getContent(),
            result.getTotalElements(),
            page,
            cappedSize
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get message history for conversation")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getMessages(id)));
    }

    @Operation(summary = "Send message — streams SSE response")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE stream"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Blank or too-long query"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Monthly quota exceeded")
    })
    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — stream duration is LLM-bounded
        AtomicReference<UUID> assistantMessageId = new AtomicReference<>();

        chatService.streamMessage(id, request.query(), assistantMessageId, null)
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
                            log.warn("stream_error conversationId={} error={}", id, error.getMessage());
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("{\"error\":\"STREAM_ERROR\",\"message\":\"" +
                                          escapeJson(error.getMessage()) + "\"}"));
                        } catch (IOException ioEx) {
                            log.error("failed to send error event", ioEx);
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
                            log.error("failed to send done event", e);
                        } finally {
                            emitter.complete();
                        }
                    }
                );

        return emitter;
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
