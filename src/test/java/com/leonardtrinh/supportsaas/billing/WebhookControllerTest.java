package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
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

    // -------------------------------------------------------------------------
    // Signature validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Signature validation")
    class SignatureValidation {

        @Test
        @DisplayName("returns 400 when Stripe signature is invalid")
        void webhook_invalidSignature_returns400() {
            String payload = "{\"id\":\"evt_bad\",\"type\":\"checkout.session.completed\"}";
            String sigHeader = "t=1,v1=badhash";

            when(stripeService.constructWebhookEvent(payload, sigHeader))
                    .thenThrow(new StripeGatewayException("Invalid signature", null));

            ResponseEntity<Map<String, Boolean>> response =
                    webhookController.webhook(payload, sigHeader);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            // WebhookService must never be called when signature is invalid
            verify(webhookService, never()).handle(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("returns 200 with received=true when Stripe signature is valid")
        void webhook_validSignature_returns200() {
            String payload = "{\"id\":\"evt_001\",\"type\":\"checkout.session.completed\"}";
            String sigHeader = "t=12345,v1=abcdef";
            Event event = mock(Event.class);

            when(stripeService.constructWebhookEvent(payload, sigHeader)).thenReturn(event);

            ResponseEntity<Map<String, Boolean>> response =
                    webhookController.webhook(payload, sigHeader);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("received", true);
            verify(webhookService).handle(event);
        }
    }

    // -------------------------------------------------------------------------
    // Idempotency — duplicate event detection lives in WebhookService.handle().
    // The controller's contract: if constructWebhookEvent succeeds, delegate to
    // handle() and return 200 regardless of whether the event was already seen.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("returns 200 and delegates to WebhookService even when event is a duplicate")
        void webhook_duplicateEvent_returns200AndDelegates() {
            // The controller does NOT own idempotency — WebhookServiceImpl does.
            // The controller must still call handle() and return 200 for duplicates
            // so that Stripe does not retry (Stripe retries on non-2xx responses).
            String payload = "{\"id\":\"evt_dup\",\"type\":\"checkout.session.completed\"}";
            String sigHeader = "t=99999,v1=validhash";
            Event event = mock(Event.class);

            when(stripeService.constructWebhookEvent(payload, sigHeader)).thenReturn(event);
            // WebhookServiceImpl.handle() silently no-ops for duplicates — simulate that
            doNothing().when(webhookService).handle(event);

            ResponseEntity<Map<String, Boolean>> response =
                    webhookController.webhook(payload, sigHeader);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("received", true);
            // Controller MUST still invoke handle() — idempotency is the service's concern
            verify(webhookService).handle(event);
        }
    }

    // -------------------------------------------------------------------------
    // Event-type routing — controller is type-agnostic; it delegates all events
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Event-type routing")
    class EventTypeRouting {

        @Test
        @DisplayName("checkout.session.completed — delegates to WebhookService and returns 200")
        void webhook_checkoutSessionCompleted_delegatesAndReturns200() {
            String payload = "{\"id\":\"evt_cs_001\",\"type\":\"checkout.session.completed\"}";
            String sigHeader = "t=11111,v1=validhash";
            Event event = mock(Event.class);

            when(stripeService.constructWebhookEvent(payload, sigHeader)).thenReturn(event);

            ResponseEntity<Map<String, Boolean>> response =
                    webhookController.webhook(payload, sigHeader);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("received", true);
            verify(webhookService).handle(event);
        }

        @Test
        @DisplayName("invoice.payment_failed — delegates to WebhookService and returns 200")
        void webhook_invoicePaymentFailed_delegatesAndReturns200() {
            String payload = "{\"id\":\"evt_inv_001\",\"type\":\"invoice.payment_failed\"}";
            String sigHeader = "t=22222,v1=validhash";
            Event event = mock(Event.class);

            when(stripeService.constructWebhookEvent(payload, sigHeader)).thenReturn(event);

            ResponseEntity<Map<String, Boolean>> response =
                    webhookController.webhook(payload, sigHeader);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("received", true);
            verify(webhookService).handle(event);
        }
    }
}
