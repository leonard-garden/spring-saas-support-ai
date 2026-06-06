package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Plan and subscription management")
public class WebhookController {

    private final StripeService stripeService;
    private final WebhookService webhookService;

    public WebhookController(StripeService stripeService, WebhookService webhookService) {
        this.stripeService = stripeService;
        this.webhookService = webhookService;
    }

    /**
     * Stripe webhook receiver. No JWT required — Stripe calls this directly.
     * Raw body must be read as String to preserve the exact bytes for HMAC signature validation.
     */
    @PostMapping(value = "/webhook", consumes = "application/json")
    @Operation(
            summary = "Stripe webhook receiver",
            description = "Receives Stripe events. Validates HMAC signature and applies idempotency gate before processing.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event received (processed or already seen)"),
        @ApiResponse(responseCode = "400", description = "Invalid Stripe signature or malformed payload")
    })
    public ResponseEntity<Map<String, Boolean>> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = stripeService.constructWebhookEvent(payload, sigHeader);
        } catch (StripeGatewayException e) {
            log.warn("webhook_signature_invalid error={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        webhookService.handle(event);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
