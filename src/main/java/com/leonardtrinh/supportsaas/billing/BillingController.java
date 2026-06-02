package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.ResourceNotFoundException;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Plan and subscription management")
public class BillingController {

    private final SubscriptionService subscriptionService;

    public BillingController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    @Operation(summary = "List available plans")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plans retrieved successfully")
    })
    public ApiResponse<List<PlanResponse>> getPlans() {
        List<PlanResponse> plans = subscriptionService.getActivePlans().stream()
                .map(PlanResponse::from)
                .toList();
        return ApiResponse.ok(plans);
    }

    @GetMapping("/subscription")
    @Operation(summary = "Get current subscription")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No active subscription")
    })
    public ApiResponse<SubscriptionResponse> getSubscription() {
        UUID tenantId = TenantContext.getTenantId();

        Subscription subscription = subscriptionService.getCurrentSubscription(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", tenantId));

        Plan plan = subscriptionService.getCurrentPlan(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", subscription.getPlanId()));

        return ApiResponse.ok(SubscriptionResponse.from(subscription, plan));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Create Stripe Checkout session")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checkout URL returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Already subscribed or invalid plan"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public ApiResponse<CheckoutResponse> createCheckout(
            @AuthenticationPrincipal JwtClaims claims,
            @Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = subscriptionService.startCheckout(claims.tenantId(), claims.email(), request.planSlug());
        return ApiResponse.ok(response);
    }

    @GetMapping("/success")
    @Operation(summary = "Billing success acknowledgement (does not activate subscription)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checkout acknowledged")
    })
    public ApiResponse<String> checkoutSuccess(
            @RequestParam(name = "session_id", required = false) String sessionId) {
        return ApiResponse.ok("Checkout initiated. Subscription will be activated after payment confirmation.");
    }
}
