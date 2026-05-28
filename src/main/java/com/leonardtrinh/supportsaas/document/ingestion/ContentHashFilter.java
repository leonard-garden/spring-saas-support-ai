package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class ContentHashFilter {

    private final DocumentChunkRepository chunkRepository;

    public ContentHashFilter(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public String computeHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public boolean isNew(UUID documentId, String hash) {
        return !chunkRepository.existsByDocumentIdAndContentHash(documentId, hash);
    }
}
