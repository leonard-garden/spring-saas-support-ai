package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class TextIngester implements DocumentIngester {

    @Override
    public boolean supports(String contentType) {
        return "text/plain".equals(contentType) || "text/markdown".equals(contentType);
    }

    @Override
    public String read(InputStream stream) throws DocumentProcessingException {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read text: " + e.getMessage());
        }
    }
}
