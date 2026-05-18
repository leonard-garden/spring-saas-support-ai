package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class IngestionRouter {

    private final PdfIngester pdfIngester;
    private final TextIngester textIngester;

    public IngestionRouter(PdfIngester pdfIngester, TextIngester textIngester) {
        this.pdfIngester = pdfIngester;
        this.textIngester = textIngester;
    }

    public String route(String contentType, InputStream stream) throws DocumentProcessingException {
        return switch (contentType) {
            case "application/pdf" -> pdfIngester.read(stream);
            case "text/plain", "text/markdown" -> textIngester.read(stream);
            default -> throw new DocumentProcessingException("Unsupported content type: " + contentType);
        };
    }
}
