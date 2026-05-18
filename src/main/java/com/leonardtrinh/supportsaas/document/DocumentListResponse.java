package com.leonardtrinh.supportsaas.document;

import java.util.List;

public record DocumentListResponse(List<DocumentResponse> documents, long total) {}
