package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/widget")
public class ChatWidgetController {

    private final WidgetAdminService widgetAdminService;

    public ChatWidgetController(WidgetAdminService widgetAdminService) {
        this.widgetAdminService = widgetAdminService;
    }

    @Operation(summary = "Get tenant widget config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Widget found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Widget not yet created")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<WidgetResponse>> getWidget() {
        return ResponseEntity.ok(ApiResponse.ok(widgetAdminService.getWidget()));
    }

    @Operation(summary = "Create widget with default config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Widget created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — ADMIN/OWNER role required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Widget already exists for this tenant")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<WidgetResponse>> createWidget() {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(widgetAdminService.createWidget()));
    }

    @Operation(summary = "Update widget config (name, color, welcome message)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Config updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Widget not found")
    })
    @PutMapping("/{id}/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<WidgetResponse>> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWidgetConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(widgetAdminService.updateConfig(id, request)));
    }

    @Operation(summary = "Replace widget knowledge bases")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "KBs updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Widget or KB not found")
    })
    @PutMapping("/{id}/knowledge-bases")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<WidgetResponse>> replaceKnowledgeBases(
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceKnowledgeBasesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(widgetAdminService.replaceKnowledgeBases(id, request)));
    }
}
