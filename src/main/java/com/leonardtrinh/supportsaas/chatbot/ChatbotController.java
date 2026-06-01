package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chatbots")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @Operation(summary = "Create chatbot config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Chatbot created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "KnowledgeBase not found")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<ChatbotResponse>> create(
            @Valid @RequestBody CreateChatbotRequest request) {
        ChatbotResponse response = chatbotService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "List chatbots for tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatbotResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.list()));
    }

    @Operation(summary = "Get chatbot by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chatbot found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatbotResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getById(id)));
    }

    @Operation(summary = "Update chatbot config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chatbot updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot or KnowledgeBase not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<ChatbotResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChatbotRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.update(id, request)));
    }

    @Operation(summary = "Delete chatbot config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Chatbot deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        chatbotService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get embed snippet for chatbot")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Embed snippet returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Chatbot not found")
    })
    @GetMapping("/{id}/embed")
    public ResponseEntity<ApiResponse<EmbedResponse>> getEmbedSnippet(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getEmbedSnippet(id)));
    }
}
