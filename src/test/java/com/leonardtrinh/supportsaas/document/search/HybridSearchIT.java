package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.BaseIT;
import com.leonardtrinh.supportsaas.document.Document;
import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.document.DocumentResponse;
import com.leonardtrinh.supportsaas.document.DocumentStatus;
import com.leonardtrinh.supportsaas.storage.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class HybridSearchIT extends BaseIT {

    @MockBean
    private MinioService minioService;

    @Autowired
    private DocumentRepository documentRepository;

    private static final byte[] DOCUMENT_CONTENT = (
            "The quick brown fox jumps over the lazy dog. " +
            "This sentence contains enough words for full text search. " +
            "Hybrid retrieval combines vector search with keyword matching."
    ).getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        configureRestTemplate();
        float[] fixedVector = new float[1536];
        Arrays.fill(fixedVector, 0.1f);
        when(embeddingModel.embed(anyString())).thenReturn(fixedVector);

        doAnswer(inv -> null).when(minioService).upload(anyString(), any(), anyLong(), anyString());
        when(minioService.download(anyString())).thenReturn(new ByteArrayInputStream(DOCUMENT_CONTENT));
    }

    @Test
    @DisplayName("POST /kb/search returns results after document is READY")
    void search_withReadyDocument_returnsResults() throws Exception {
        AuthResponse auth = doSignup(uniqueName("SearchBiz"), uniqueEmail("search"));
        uploadAndWaitReady(auth, "search-doc.txt");

        SearchRequest req = new SearchRequest();
        req.setQuery("quick brown fox");
        req.setTopK(5);

        ResponseEntity<ApiResponse<List<SearchResult>>> resp = restTemplate.exchange(
                "/api/v1/kb/search",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(auth.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().success()).isTrue();
        assertThat(resp.getBody().data()).isNotEmpty();
    }

    @Test
    @DisplayName("tenant isolation: tenant B cannot see tenant A's chunks")
    void search_tenantIsolation_returnsEmptyForOtherTenant() throws Exception {
        AuthResponse tenantA = doSignup(uniqueName("TenantA"), uniqueEmail("tenantA"));
        uploadAndWaitReady(tenantA, "tenant-a-doc.txt");

        AuthResponse tenantB = doSignup(uniqueName("TenantB"), uniqueEmail("tenantB"));

        SearchRequest req = new SearchRequest();
        req.setQuery("quick brown fox");
        req.setTopK(5);

        ResponseEntity<ApiResponse<List<SearchResult>>> resp = restTemplate.exchange(
                "/api/v1/kb/search",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(tenantB.accessToken())),
                new ParameterizedTypeReference<>() {}
        );

        // Tenant B has no READY docs → 422
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("POST /kb/search returns 422 when no READY documents exist")
    void search_noReadyDocs_returns422() {
        AuthResponse auth = doSignup(uniqueName("EmptyBiz"), uniqueEmail("empty"));

        SearchRequest req = new SearchRequest();
        req.setQuery("some query");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/kb/search",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeader(auth.accessToken())),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void uploadAndWaitReady(AuthResponse auth, String filename) throws Exception {
        HttpHeaders headers = authHeader(auth.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.TEXT_PLAIN);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(DOCUMENT_CONTENT) {
            @Override
            public String getFilename() { return filename; }
        }, partHeaders));

        ResponseEntity<ApiResponse<DocumentResponse>> uploadResp = restTemplate.exchange(
                "/api/v1/kb/documents",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(uploadResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID documentId = uploadResp.getBody().data().id();

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Document doc = documentRepository.findById(documentId).orElseThrow();
            if (doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.FAILED) {
                assertThat(doc.getStatus())
                        .as("Document should be READY but was %s: %s", doc.getStatus(), doc.getErrorMessage())
                        .isEqualTo(DocumentStatus.READY);
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Document did not reach READY within timeout");
    }
}
