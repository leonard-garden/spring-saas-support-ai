package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBase;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.storage.MinioService;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "text/plain", "text/markdown"
    );

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final MinioService minioService;
    private final DocumentProcessingService processingService;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                                KnowledgeBaseRepository knowledgeBaseRepository,
                                MinioService minioService,
                                DocumentProcessingService processingService) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.minioService = minioService;
        this.processingService = processingService;
    }

    @Override
    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        validate(file);

        UUID tenantId = TenantContext.getTenantId();
        KnowledgeBase kb = knowledgeBaseRepository.findByBusinessId(tenantId)
                .orElseGet(() -> {
                    KnowledgeBase newKb = new KnowledgeBase();
                    newKb.setBusinessId(tenantId);
                    return knowledgeBaseRepository.save(newKb);
                });

        UUID documentId = UUID.randomUUID();
        String objectKey = tenantId + "/" + documentId + "/" + sanitizeFilename(file.getOriginalFilename());

        minioService.upload(objectKey, getInputStream(file), file.getSize(), file.getContentType());

        Document doc = new Document();
        doc.setBusinessId(tenantId);
        doc.setKnowledgeBaseId(kb.getId());
        doc.setFilename(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setMinioKey(objectKey);
        doc.setStatus(DocumentStatus.PENDING);

        try {
            doc = documentRepository.save(doc);
        } catch (RuntimeException dbEx) {
            try {
                minioService.delete(objectKey);
            } catch (RuntimeException cleanupEx) {
                dbEx.addSuppressed(cleanupEx);
            }
            throw dbEx;
        }

        processingService.processAsync(doc.getId(), tenantId);

        return toResponse(doc);
    }

    @Override
    public List<DocumentResponse> listAll(DocumentStatus status) {
        UUID tenantId = TenantContext.getTenantId();
        KnowledgeBase kb = knowledgeBaseRepository.findByBusinessId(tenantId).orElse(null);
        if (kb == null) return List.of();
        List<Document> docs = (status != null)
                ? documentRepository.findAllByKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(kb.getId(), status)
                : documentRepository.findAllByKnowledgeBaseIdOrderByCreatedAtDesc(kb.getId());
        return docs.stream().map(this::toResponse).toList();
    }

    @Override
    public DocumentResponse getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Document doc = documentRepository.findByIdAndBusinessId(id, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return toResponse(doc);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Document doc = documentRepository.findByIdAndBusinessId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentInProgressException();
        }
        minioService.delete(doc.getMinioKey());
        documentRepository.delete(doc);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("File is required");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new FileValidationException("File exceeds maximum size of 10MB");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new FileValidationException("File type not supported. Allowed: pdf, txt, md");
        }
    }

    private InputStream getInputStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new FileValidationException("Failed to read file: " + e.getMessage());
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Integer chunkCountForResponse(Document doc) {
        if (doc.getStatus() != DocumentStatus.READY && doc.getChunkCount() == 0) return null;
        return doc.getChunkCount();
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getStatus(),
                doc.getSizeBytes(),
                chunkCountForResponse(doc),
                doc.getErrorMessage(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
