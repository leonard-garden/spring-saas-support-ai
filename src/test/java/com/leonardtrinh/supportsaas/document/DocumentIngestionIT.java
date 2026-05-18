package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.BaseIT;
import com.leonardtrinh.supportsaas.document.chunk.DocumentChunkRepository;
import com.leonardtrinh.supportsaas.storage.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class DocumentIngestionIT extends BaseIT {

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private MinioService minioService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @BeforeEach
    void setUp() {
        configureRestTemplate();

        // Return a fixed 1536-dimensional vector for all embedding calls — avoids real OpenAI calls
        float[] fixedVector = new float[1536];
        Arrays.fill(fixedVector, 0.1f);
        when(embeddingModel.embed(anyString())).thenReturn(fixedVector);
    }

    @Test
    @DisplayName("upload: plaintext document processed async → status READY with chunks")
    void upload_plaintextDocument_becomesReadyWithChunks() throws Exception {
        AuthResponse auth = doSignup(uniqueName("IngestionBiz"), uniqueEmail("ingestion"));

        byte[] content = ("This is a test document for ingestion. " +
                "It contains enough text to produce at least one chunk after splitting. " +
                "The ingestion pipeline should extract this text, embed it, and persist chunks.")
                .getBytes(StandardCharsets.UTF_8);

        // MinioService is mocked — capture the upload call so download returns the same bytes
        org.mockito.stubbing.Answer<Void> noOp = inv -> null;
        org.mockito.Mockito.doAnswer(noOp).when(minioService).upload(
                anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                anyString());
        when(minioService.download(anyString()))
                .thenReturn(new java.io.ByteArrayInputStream(content));

        // POST multipart/form-data to /api/v1/kb/documents
        HttpHeaders headers = authHeader(auth.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<byte[]> filePart = new HttpEntity<>(content, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(content) {
            @Override
            public String getFilename() { return "test.txt"; }
        }, partHeaders));

        ResponseEntity<ApiResponse<DocumentResponse>> uploadResp = restTemplate.exchange(
                "/api/v1/kb/documents",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(uploadResp.getBody()).isNotNull();
        UUID documentId = uploadResp.getBody().data().id();
        assertThat(documentId).isNotNull();

        // Poll for READY status — async processing, timeout 5 seconds
        Document doc = pollForStatus(documentId, DocumentStatus.READY, 5_000);
        assertThat(doc.getStatus())
                .as("Expected READY but was %s (error: %s)", doc.getStatus(), doc.getErrorMessage())
                .isEqualTo(DocumentStatus.READY);

        // Assert at least one chunk was persisted
        int chunkCount = documentChunkRepository.countByDocumentId(documentId);
        assertThat(chunkCount).isGreaterThan(0);
    }

    /**
     * Polls documentRepository until the document reaches the expected status or timeout elapses.
     */
    private Document pollForStatus(UUID documentId, DocumentStatus expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Document doc = documentRepository.findById(documentId)
                    .orElseThrow(() -> new AssertionError("Document not found: " + documentId));
            if (doc.getStatus() == expected || doc.getStatus() == DocumentStatus.FAILED) {
                return doc;
            }
            Thread.sleep(200);
        }
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new AssertionError("Document not found: " + documentId));
    }
}
