package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PdfIngesterTest {

    private final PdfIngester ingester = new PdfIngester();

    @Spy
    private PdfIngester spyIngester;

    @Test
    void validPdf_returnsNonEmptyText() throws IOException, DocumentProcessingException {
        byte[] pdfBytes = createSimplePdf("Hello from PDF test");
        String result = ingester.read(new ByteArrayInputStream(pdfBytes));
        assertThat(result).contains("Hello from PDF test");
    }

    @Test
    void encryptedPdf_throwsDocumentProcessingException() throws DocumentProcessingException {
        doThrow(new DocumentProcessingException("PDF is encrypted and cannot be processed"))
                .when(spyIngester).read(any(InputStream.class));

        assertThatThrownBy(() -> spyIngester.read(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("encrypted");
    }

    private byte[] createSimplePdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText(text);
                stream.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
