package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class IngestionRouter {

    private final List<DocumentIngester> ingesters;

    public IngestionRouter(List<DocumentIngester> ingesters) {
        this.ingesters = ingesters;
    }

    public String route(String contentType, InputStream stream) throws DocumentProcessingException {
        return ingesters.stream()
                .filter(i -> i.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new DocumentProcessingException("Unsupported content type: " + contentType))
                .read(stream);
    }
}
