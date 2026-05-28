package com.leonardtrinh.supportsaas.document.search;

import java.util.UUID;

public record SearchResult(
        UUID chunkId,
        String content,
        double score,
        UUID documentId,
        String documentName,
        int chunkIndex,
        SearchSource source
) {}
