package com.leonardtrinh.supportsaas.document.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChunkTextSplitter {

    private final TokenTextSplitter tokenTextSplitter;

    public ChunkTextSplitter() {
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }

    public List<String> split(String text) {
        Document doc = new Document(text);
        return tokenTextSplitter.apply(List.of(doc))
                .stream()
                .map(Document::getText)
                .toList();
    }
}
