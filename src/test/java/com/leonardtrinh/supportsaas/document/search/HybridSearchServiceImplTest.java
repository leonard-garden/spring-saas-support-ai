package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.document.DocumentStatus;
import com.leonardtrinh.supportsaas.document.ingestion.VectorStorage;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HybridSearchServiceImplTest {

    @Mock private VectorStorage vectorStorage;
    @Mock private FullTextStorage fullTextStorage;
    @Mock private DocumentRepository documentRepository;
    @Mock private Executor taskExecutor;

    private HybridSearchServiceImpl service;

    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
        service = new HybridSearchServiceImpl(vectorStorage, fullTextStorage, documentRepository, taskExecutor);
    }

    @Test
    @DisplayName("throws NoReadyDocumentsException when no READY docs exist")
    void search_noReadyDocs_throwsException() {
        when(documentRepository.existsByStatus(DocumentStatus.READY)).thenReturn(false);

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getTenantId).thenReturn(businessId);
            assertThatThrownBy(() -> service.search("test query", 10))
                    .isInstanceOf(NoReadyDocumentsException.class);
        }
    }

    @Test
    @DisplayName("returns VECTOR results when FTS finds nothing")
    void search_vectorOnly_returnsVectorResults() {
        when(documentRepository.existsByStatus(DocumentStatus.READY)).thenReturn(true);
        String chunkId = UUID.randomUUID().toString();
        String docId = UUID.randomUUID().toString();
        when(vectorStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(rows(row(chunkId, docId, 0, "chunk content", 0.9, "file.txt")));
        when(fullTextStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getTenantId).thenReturn(businessId);
            List<SearchResult> results = service.search("test query", 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).source()).isEqualTo(SearchSource.VECTOR);
            assertThat(results.get(0).score()).isEqualTo(0.9f, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    @Test
    @DisplayName("returns FTS results when vector finds nothing")
    void search_ftsOnly_returnsFtsResults() {
        when(documentRepository.existsByStatus(DocumentStatus.READY)).thenReturn(true);
        String ftsChunkId = UUID.randomUUID().toString();
        String ftsDocId = UUID.randomUUID().toString();
        when(vectorStorage.search(anyString(), any(UUID.class), anyInt())).thenReturn(Collections.emptyList());
        when(fullTextStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(rows(row(ftsChunkId, ftsDocId, 1, "fts chunk", 0.7, "file.txt")));

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getTenantId).thenReturn(businessId);
            List<SearchResult> results = service.search("test query", 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).source()).isEqualTo(SearchSource.FTS);
        }
    }

    @Test
    @DisplayName("same chunk found by both sources merged with source=BOTH and max score")
    void search_sameChunkInBoth_mergedWithBothAndMaxScore() {
        String sharedId = UUID.randomUUID().toString();
        String docId = UUID.randomUUID().toString();

        when(documentRepository.existsByStatus(DocumentStatus.READY)).thenReturn(true);
        when(vectorStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(rows(row(sharedId, docId, 0, "shared content", 0.85, "doc.txt")));
        when(fullTextStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(rows(row(sharedId, docId, 0, "shared content", 0.6, "doc.txt")));

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getTenantId).thenReturn(businessId);
            List<SearchResult> results = service.search("test query", 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).source()).isEqualTo(SearchSource.BOTH);
            assertThat(results.get(0).score()).isGreaterThan(0.8);
        }
    }

    @Test
    @DisplayName("results are sorted by score descending")
    void search_resultsSortedByScoreDesc() {
        when(documentRepository.existsByStatus(DocumentStatus.READY)).thenReturn(true);
        String sortDocId = UUID.randomUUID().toString();
        when(vectorStorage.search(anyString(), any(UUID.class), anyInt()))
                .thenReturn(rows(
                        row(UUID.randomUUID().toString(), sortDocId, 0, "low score", 0.3, "a.txt"),
                        row(UUID.randomUUID().toString(), sortDocId, 1, "high score", 0.95, "a.txt")
                ));
        when(fullTextStorage.search(anyString(), any(UUID.class), anyInt())).thenReturn(Collections.emptyList());

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getTenantId).thenReturn(businessId);
            List<SearchResult> results = service.search("test query", 10);
            assertThat(results).hasSize(2);
            assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        }
    }

    private Object[] row(String id, String docId, int chunkIdx, String content, double score, String docName) {
        return new Object[]{id, docId, chunkIdx, content, (float) score, docName};
    }

    private List<Object[]> rows(Object[]... rowArray) {
        List<Object[]> list = new ArrayList<>(rowArray.length);
        for (Object[] r : rowArray) {
            list.add(r);
        }
        return list;
    }
}
