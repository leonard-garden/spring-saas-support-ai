package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import com.leonardtrinh.supportsaas.document.ingestion.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final com.leonardtrinh.supportsaas.storage.MinioService minioService;
    private final IngestionRouter ingestionRouter;
    private final ChunkTextSplitter chunkTextSplitter;
    private final ContentHashFilter contentHashFilter;
    private final VectorStorage vectorStorage;
    private final DocumentChunkRepository chunkRepository;
    private final TransactionTemplate requiresNewTx;

    public DocumentProcessingServiceImpl(DocumentRepository documentRepository,
                                         com.leonardtrinh.supportsaas.storage.MinioService minioService,
                                         IngestionRouter ingestionRouter,
                                         ChunkTextSplitter chunkTextSplitter,
                                         ContentHashFilter contentHashFilter,
                                         VectorStorage vectorStorage,
                                         DocumentChunkRepository chunkRepository,
                                         PlatformTransactionManager txManager) {
        this.documentRepository = documentRepository;
        this.minioService = minioService;
        this.ingestionRouter = ingestionRouter;
        this.chunkTextSplitter = chunkTextSplitter;
        this.contentHashFilter = contentHashFilter;
        this.vectorStorage = vectorStorage;
        this.chunkRepository = chunkRepository;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Async("processingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAsync(UUID documentId, UUID businessId) {
        Document doc = documentRepository.findByIdAndBusinessId(documentId, businessId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        try (InputStream stream = minioService.download(doc.getMinioKey())) {
            String rawText = ingestionRouter.route(doc.getContentType(), stream);
            List<String> chunks = chunkTextSplitter.split(rawText);

            int savedCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                String hash = contentHashFilter.computeHash(chunk);
                if (!contentHashFilter.isNew(documentId, hash)) {
                    continue;
                }
                vectorStorage.store(chunk, hash, documentId, businessId, i);
                savedCount++;
            }

            doc.setStatus(DocumentStatus.READY);
            doc.setChunkCount(savedCount);
            doc.setUpdatedAt(Instant.now());
            documentRepository.save(doc);

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // The main transaction is aborted at this point — any DB call on the same
            // connection fails with "current transaction is aborted". Run markFailed in
            // a fresh transaction so the document always reaches a terminal status.
            // Chunk cleanup is handled by the outer transaction rollback.
            String errorMessage = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), 500);
            requiresNewTx.execute(status -> {
                markFailed(doc, errorMessage);
                return null;
            });
        }
    }

    private void markFailed(Document doc, String message) {
        doc.setStatus(DocumentStatus.FAILED);
        doc.setErrorMessage(message);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
    }

    private String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
