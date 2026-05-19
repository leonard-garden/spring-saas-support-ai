package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;

import java.io.InputStream;

public interface DocumentIngester {
    String read(InputStream stream) throws DocumentProcessingException;
    boolean supports(String contentType);
}
