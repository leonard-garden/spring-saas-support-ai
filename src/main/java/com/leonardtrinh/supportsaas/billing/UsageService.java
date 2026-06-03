package com.leonardtrinh.supportsaas.billing;

import java.util.UUID;

public interface UsageService {
    UsageResponse getUsage(UUID businessId);
}
