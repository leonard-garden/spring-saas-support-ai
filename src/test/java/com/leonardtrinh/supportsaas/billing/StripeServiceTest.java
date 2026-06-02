package com.leonardtrinh.supportsaas.billing;

import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.SubscriptionScheduleCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    private StripeServiceImpl stripeService;

    @BeforeEach
    void setUp() {
        StripeProperties props = new StripeProperties("sk_test_key", "whsec_test");
        stripeService = new StripeServiceImpl(props);
    }

    @Test
    @DisplayName("getOrCreateCustomer returns existing customer when found by businessId metadata")
    void getOrCreateCustomer_existingCustomer_returnsExisting() throws Exception {
        UUID businessId = UUID.randomUUID();
        Customer existing = mock(Customer.class);
        CustomerSearchResult searchResult = mock(CustomerSearchResult.class);
        when(searchResult.getData()).thenReturn(List.of(existing));

        try (MockedStatic<Customer> mockedCustomer = mockStatic(Customer.class)) {
            mockedCustomer.when(() -> Customer.search(any(CustomerSearchParams.class)))
                    .thenReturn(searchResult);

            Customer result = stripeService.getOrCreateCustomer("user@example.com", businessId);

            assertThat(result).isSameAs(existing);
            mockedCustomer.verify(() -> Customer.create(any(CustomerCreateParams.class)), org.mockito.Mockito.never());
        }
    }

    @Test
    @DisplayName("getOrCreateCustomer creates new customer when none found by businessId metadata")
    void getOrCreateCustomer_noExistingCustomer_createsNew() throws Exception {
        UUID businessId = UUID.randomUUID();
        Customer created = mock(Customer.class);
        CustomerSearchResult searchResult = mock(CustomerSearchResult.class);
        when(searchResult.getData()).thenReturn(List.of());

        try (MockedStatic<Customer> mockedCustomer = mockStatic(Customer.class)) {
            mockedCustomer.when(() -> Customer.search(any(CustomerSearchParams.class)))
                    .thenReturn(searchResult);
            mockedCustomer.when(() -> Customer.create(any(CustomerCreateParams.class)))
                    .thenReturn(created);

            Customer result = stripeService.getOrCreateCustomer("new@example.com", businessId);

            assertThat(result).isSameAs(created);
            mockedCustomer.verify(() -> Customer.create(any(CustomerCreateParams.class)));
        }
    }

    @Test
    @DisplayName("getOrCreateCustomer wraps StripeException in StripeGatewayException")
    void getOrCreateCustomer_stripeException_throwsStripeGatewayException() {
        UUID businessId = UUID.randomUUID();

        try (MockedStatic<Customer> mockedCustomer = mockStatic(Customer.class)) {
            mockedCustomer.when(() -> Customer.search(any(CustomerSearchParams.class)))
                    .thenThrow(new com.stripe.exception.ApiException("error", "req_1", "err", 500, null));

            assertThatThrownBy(() -> stripeService.getOrCreateCustomer("user@example.com", businessId))
                    .isInstanceOf(StripeGatewayException.class)
                    .hasMessageContaining("Failed to get or create Stripe customer");
        }
    }

    @Test
    @DisplayName("createCheckoutSession returns session with idempotency key applied")
    void createCheckoutSession_happyPath_returnsSession() throws Exception {
        Session session = mock(Session.class);

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(session);

            Session result = stripeService.createCheckoutSession(
                    "cus_123", "price_456",
                    "https://example.com/success", "https://example.com/cancel",
                    "idem_key_1");

            assertThat(result).isSameAs(session);
        }
    }

    @Test
    @DisplayName("cancelAtPeriodEnd sets cancel_at_period_end with idempotency key")
    void cancelAtPeriodEnd_happyPath_setsCancelAtPeriodEnd() throws Exception {
        Subscription subscription = mock(Subscription.class);
        Subscription updated = mock(Subscription.class);

        try (MockedStatic<Subscription> mockedSub = mockStatic(Subscription.class)) {
            mockedSub.when(() -> Subscription.retrieve("sub_123")).thenReturn(subscription);
            when(subscription.update(any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                    .thenReturn(updated);

            Subscription result = stripeService.cancelAtPeriodEnd("sub_123", "idem_cancel_1");

            assertThat(result).isSameAs(updated);
        }
    }

    @Test
    @DisplayName("updateSubscription applies proration with idempotency key")
    void updateSubscription_happyPath_appliesProration() throws Exception {
        Subscription subscription = mock(Subscription.class);
        Subscription updated = mock(Subscription.class);
        SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        SubscriptionItem item = mock(SubscriptionItem.class);

        when(item.getId()).thenReturn("si_abc");
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(subscription.getItems()).thenReturn(itemCollection);

        try (MockedStatic<Subscription> mockedSub = mockStatic(Subscription.class)) {
            mockedSub.when(() -> Subscription.retrieve("sub_456")).thenReturn(subscription);
            when(subscription.update(any(SubscriptionUpdateParams.class), any(RequestOptions.class)))
                    .thenReturn(updated);

            Subscription result = stripeService.updateSubscription("sub_456", "price_new", "idem_update_1");

            assertThat(result).isSameAs(updated);
        }
    }

    @Test
    @DisplayName("scheduleSubscriptionUpdate creates schedule with idempotency key")
    void scheduleSubscriptionUpdate_happyPath_createsSchedule() throws Exception {
        SubscriptionSchedule schedule = mock(SubscriptionSchedule.class);

        try (MockedStatic<SubscriptionSchedule> mockedSchedule = mockStatic(SubscriptionSchedule.class)) {
            mockedSchedule.when(() -> SubscriptionSchedule.create(
                            any(SubscriptionScheduleCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(schedule);

            SubscriptionSchedule result = stripeService.scheduleSubscriptionUpdate(
                    "sub_789", "price_future", "idem_sched_1");

            assertThat(result).isSameAs(schedule);
        }
    }

    @Test
    @DisplayName("retrieveSubscription returns subscription by id")
    void retrieveSubscription_happyPath_returnsSubscription() throws Exception {
        Subscription subscription = mock(Subscription.class);

        try (MockedStatic<Subscription> mockedSub = mockStatic(Subscription.class)) {
            mockedSub.when(() -> Subscription.retrieve("sub_retrieve")).thenReturn(subscription);

            Subscription result = stripeService.retrieveSubscription("sub_retrieve");

            assertThat(result).isSameAs(subscription);
        }
    }

    @Test
    @DisplayName("constructWebhookEvent wraps exception in StripeGatewayException")
    void constructWebhookEvent_stripeException_throwsStripeGatewayException() throws Exception {
        try (MockedStatic<Webhook> mockedWebhook = mockStatic(Webhook.class)) {
            mockedWebhook.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new com.stripe.exception.SignatureVerificationException("bad signature", "sig_header"));

            assertThatThrownBy(() -> stripeService.constructWebhookEvent("payload", "sig_header"))
                    .isInstanceOf(StripeGatewayException.class)
                    .hasMessageContaining("Failed to construct webhook event");
        }
    }
}
