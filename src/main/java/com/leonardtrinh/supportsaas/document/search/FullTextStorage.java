package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class FullTextStorage {

    private final DocumentChunkRepository chunkRepository;

    public FullTextStorage(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<Object[]> search(String query, UUID businessId, int topK) {
        return chunkRepository.ftsSearch(query, businessId.toString(), topK);
    }
}
