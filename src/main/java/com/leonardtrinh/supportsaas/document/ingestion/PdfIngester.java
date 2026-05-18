package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfIngester implements DocumentIngester {

    @Override
    public String read(InputStream stream) throws DocumentProcessingException {
        try {
            byte[] bytes = stream.readAllBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(doc);
            }
        } catch (InvalidPasswordException e) {
            throw new DocumentProcessingException("PDF is encrypted and cannot be processed");
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read PDF: " + e.getMessage());
        }
    }
}
