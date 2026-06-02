package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private StripeService stripeService;

    @Mock
    private WebhookService webhookService;

    private WebhookController webhookController;

    @BeforeEach
    void setUp() {
        webhookController = new WebhookController(stripeService, webhookService);
    }

    @Test
    @DisplayName("webhook — returns 200 with received=true on valid signature")
    void webhook_validSignature_returns200() {
        String payload = "{\"id\":\"evt_001\",\"type\":\"checkout.session.completed\"}";
        String sigHeader = "t=12345,v1=abcdef";
        Event event = mock(Event.class);

        when(stripeService.constructWebhookEvent(payload, sigHeader)).thenReturn(event);

        ResponseEntity<Map<String, Boolean>> response = webhookController.webhook(payload, sigHeader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("received", true);
        verify(webhookService).handle(event);
    }

    @Test
    @DisplayName("webhook — returns 400 when signature verification fails")
    void webhook_invalidSignature_returns400() {
        String payload = "{\"id\":\"evt_002\"}";
        String sigHeader = "t=bad,v1=bad";

        when(stripeService.constructWebhookEvent(payload, sigHeader))
                .thenThrow(new StripeGatewayException("Invalid signature", null));

        ResponseEntity<Map<String, Boolean>> response = webhookController.webhook(payload, sigHeader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(webhookService, never()).handle(any());
    }

    // Helper to satisfy compiler — avoids raw type warning on never().handle(any())
    @SuppressWarnings("SameParameterValue")
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
