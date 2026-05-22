package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.document.DocumentStatus;
import com.leonardtrinh.supportsaas.document.ingestion.VectorStorage;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class HybridSearchServiceImpl implements HybridSearchService {

    private final VectorStorage vectorStorage;
    private final FullTextStorage fullTextStorage;
    private final DocumentRepository documentRepository;
    private final Executor taskExecutor;

    public HybridSearchServiceImpl(
            VectorStorage vectorStorage,
            FullTextStorage fullTextStorage,
            DocumentRepository documentRepository,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.vectorStorage = vectorStorage;
        this.fullTextStorage = fullTextStorage;
        this.documentRepository = documentRepository;
        this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, int topK) {
        if (!documentRepository.existsByStatus(DocumentStatus.READY)) {
            throw new NoReadyDocumentsException();
        }

        UUID businessId = TenantContext.getTenantId();

        CompletableFuture<List<SearchResult>> vectorFuture = CompletableFuture
                .supplyAsync(() -> toSearchResults(vectorStorage.search(query, businessId, topK), SearchSource.VECTOR), taskExecutor);
        CompletableFuture<List<SearchResult>> ftsFuture = CompletableFuture
                .supplyAsync(() -> toSearchResults(fullTextStorage.search(query, businessId, topK), SearchSource.FTS), taskExecutor);

        CompletableFuture.allOf(vectorFuture, ftsFuture).join();

        Map<UUID, SearchResult> merged = new LinkedHashMap<>();
        for (SearchResult result : vectorFuture.join()) {
            merged.put(result.chunkId(), result);
        }
        for (SearchResult result : ftsFuture.join()) {
            merged.merge(result.chunkId(), result, (existing, incoming) ->
                    new SearchResult(
                            existing.chunkId(),
                            existing.content(),
                            Math.max(existing.score(), incoming.score()),
                            existing.documentId(),
                            existing.documentName(),
                            existing.chunkIndex(),
                            SearchSource.BOTH
                    )
            );
        }

        List<SearchResult> results = new ArrayList<>(merged.values());
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results;
    }

    private List<SearchResult> toSearchResults(List<Object[]> rows, SearchSource source) {
        List<SearchResult> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            results.add(new SearchResult(
                    UUID.fromString((String) row[0]),
                    (String) row[3],
                    ((Number) row[4]).doubleValue(),
                    UUID.fromString((String) row[1]),
                    (String) row[5],
                    ((Number) row[2]).intValue(),
                    source
            ));
        }
        return results;
    }
}
