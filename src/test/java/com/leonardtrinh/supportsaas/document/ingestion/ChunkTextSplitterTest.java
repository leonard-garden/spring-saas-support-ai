package com.leonardtrinh.supportsaas.document.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkTextSplitterTest {

    private final ChunkTextSplitter splitter = new ChunkTextSplitter();

    @Test
    void shortText_returnsSingleChunk() {
        List<String> chunks = splitter.split("This is a short text.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isNotEmpty();
    }

    @Test
    void longText_returnsMultipleChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("word").append(i).append(" ");
        }
        List<String> chunks = splitter.split(sb.toString());
        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void chunks_areNonEmptyStrings() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("word").append(i).append(" ");
        }
        List<String> chunks = splitter.split(sb.toString());
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }
}
