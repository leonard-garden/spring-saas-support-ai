package com.leonardtrinh.supportsaas.chat;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    long total,
    int page,
    int limit
) {}
