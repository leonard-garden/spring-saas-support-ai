package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBase;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseNotFoundException;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseRepository;
import com.leonardtrinh.supportsaas.storage.MinioService;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private MinioService minioService;

    private DocumentServiceImpl documentService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(documentRepository, knowledgeBaseRepository, minioService);
    }

    // --- helpers ---

    private KnowledgeBase makeKb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setBusinessId(TENANT_ID);
        return kb;
    }

    private Document makeDocument(DocumentStatus status) {
        Document doc = new Document();
        doc.setBusinessId(TENANT_ID);
        doc.setKnowledgeBaseId(KB_ID);
        doc.setFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setSizeBytes(1024L);
        doc.setMinioKey(TENANT_ID + "/some-key/test.pdf");
        doc.setStatus(status);
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    // --- upload tests ---

    @Test
    @DisplayName("upload_persistsPendingDocument_andUploadsToMinio")
    void upload_persistsPendingDocument_andUploadsToMinio() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", new byte[512]);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);

            when(knowledgeBaseRepository.findByBusinessId(TENANT_ID)).thenReturn(Optional.of(makeKb()));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            DocumentResponse response = documentService.upload(file);

            verify(minioService).upload(anyString(), any(), anyLong(), eq("application/pdf"));
            verify(documentRepository).save(any(Document.class));
            assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
            assertThat(response.filename()).isEqualTo("report.pdf");
        }
    }

    @Test
    @DisplayName("upload_rejectsFileOver10MB")
    void upload_rejectsFileOver10MB() {
        byte[] bigContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", bigContent);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);

            assertThatThrownBy(() -> documentService.upload(file))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("10MB");
        }
    }

    @Test
    @DisplayName("upload_rejectsUnsupportedContentType")
    void upload_rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[100]);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);

            assertThatThrownBy(() -> documentService.upload(file))
                    .isInstanceOf(FileValidationException.class)
                    .hasMessageContaining("not supported");
        }
    }

    @Test
    @DisplayName("upload_rollsBackMinioWhenDbFails")
    void upload_rollsBackMinioWhenDbFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", new byte[100]);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);

            when(knowledgeBaseRepository.findByBusinessId(TENANT_ID)).thenReturn(Optional.of(makeKb()));
            doNothing().when(minioService).upload(anyString(), any(), anyLong(), anyString());
            when(documentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> documentService.upload(file))
                    .isInstanceOf(RuntimeException.class);

            verify(minioService).delete(anyString());
        }
    }

    // --- delete tests ---

    @Test
    @DisplayName("delete_whenStatusIsProcessing_throwsDocumentInProgressException")
    void delete_whenStatusIsProcessing_throwsDocumentInProgressException() {
        UUID docId = UUID.randomUUID();
        Document doc = makeDocument(DocumentStatus.PROCESSING);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(documentRepository.findByIdAndBusinessId(docId, TENANT_ID)).thenReturn(Optional.of(doc));

            assertThatThrownBy(() -> documentService.delete(docId))
                    .isInstanceOf(DocumentInProgressException.class);

            verify(minioService, never()).delete(anyString());
            verify(documentRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("delete_whenNotFound_throwsDocumentNotFoundException")
    void delete_whenNotFound_throwsDocumentNotFoundException() {
        UUID docId = UUID.randomUUID();

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(documentRepository.findByIdAndBusinessId(docId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.delete(docId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }

    @Test
    @DisplayName("delete_happyPath_deletesMinioThenDb")
    void delete_happyPath_deletesMinioThenDb() {
        UUID docId = UUID.randomUUID();
        Document doc = makeDocument(DocumentStatus.PENDING);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(documentRepository.findByIdAndBusinessId(docId, TENANT_ID)).thenReturn(Optional.of(doc));

            documentService.delete(docId);

            verify(minioService).delete(doc.getMinioKey());
            verify(documentRepository).delete(doc);
        }
    }

    // --- listAll tests ---

    @Test
    @DisplayName("listAll_withoutStatusFilter_returnsAllDocs")
    void listAll_withoutStatusFilter_returnsAllDocs() {
        Document doc1 = makeDocument(DocumentStatus.PENDING);
        Document doc2 = makeDocument(DocumentStatus.READY);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);

            when(knowledgeBaseRepository.findByBusinessId(TENANT_ID)).thenReturn(Optional.of(makeKb()));
            when(documentRepository.findAllByKnowledgeBaseIdOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of(doc1, doc2));

            List<DocumentResponse> result = documentService.listAll(null);

            assertThat(result).hasSize(2);
        }
    }

    // --- getById tests ---

    @Test
    @DisplayName("getById_whenNotFound_throws")
    void getById_whenNotFound_throws() {
        UUID docId = UUID.randomUUID();

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(documentRepository.findByIdAndBusinessId(docId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> documentService.getById(docId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getById_documentBelongsToOtherTenant_throwsDocumentNotFoundException")
    void getById_documentBelongsToOtherTenant_throwsDocumentNotFoundException() {
        UUID otherId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(tenantId);
            when(documentRepository.findByIdAndBusinessId(otherId, tenantId))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> documentService.getById(otherId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }
    }

    @Test
    @DisplayName("delete_documentBelongsToOtherTenant_throwsDocumentNotFoundException")
    void delete_documentBelongsToOtherTenant_throwsDocumentNotFoundException() {
        UUID otherId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(tenantId);
            when(documentRepository.findByIdAndBusinessId(otherId, tenantId))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> documentService.delete(otherId))
                    .isInstanceOf(DocumentNotFoundException.class);
            verifyNoInteractions(minioService);
        }
    }
}
