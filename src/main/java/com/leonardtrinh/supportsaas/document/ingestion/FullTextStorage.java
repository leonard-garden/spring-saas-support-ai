package com.leonardtrinh.supportsaas.document.ingestion;

import org.springframework.stereotype.Component;

import java.util.UUID;

// tsv is populated at INSERT time via to_tsvector in DocumentChunkRepository.insertChunk
@Component
public class FullTextStorage {

    public void updateTsv(UUID chunkId) {
        // no-op: tsv column is already populated during INSERT in VectorStorage
    }
}
