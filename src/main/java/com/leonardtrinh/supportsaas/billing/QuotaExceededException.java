package com.leonardtrinh.supportsaas.billing;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class QuotaExceededException extends AppException {

    private final String metric;
    private final long limit;
    private final long current;

    public QuotaExceededException(String metric, long limit, long current) {
        super(HttpStatus.FORBIDDEN, "BILLING_QUOTA_EXCEEDED",
                "You have reached the limit of " + limit + " " + metric + " for your plan.");
        this.metric = metric;
        this.limit = limit;
        this.current = current;
    }

    public String getMetric() { return metric; }
    public long getLimit() { return limit; }
    public long getCurrent() { return current; }
}
