package com.leonardtrinh.supportsaas.billing;

import java.time.Instant;

public record CancelSubscriptionResponse(boolean cancelAtPeriodEnd, Instant currentPeriodEnd) {}
