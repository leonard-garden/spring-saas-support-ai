package com.leonardtrinh.supportsaas.document.ingestion;

import com.leonardtrinh.supportsaas.document.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextIngesterTest {

    private final TextIngester ingester = new TextIngester();

    @Test
    void utf8Text_returnsSameText() throws DocumentProcessingException {
        String input = "Hello, world! UTF-8 text.";
        String result = ingester.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        assertThat(result).isEqualTo(input);
    }

    @Test
    void emptyStream_returnsEmptyString() throws DocumentProcessingException {
        String result = ingester.read(new ByteArrayInputStream(new byte[0]));
        assertThat(result).isEmpty();
    }

    @Test
    void markdownContent_returnsRawText() throws DocumentProcessingException {
        String markdown = "# Heading\n\n**Bold** text with `code` and [link](http://example.com)";
        String result = ingester.read(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
        assertThat(result).isEqualTo(markdown);
    }
}
