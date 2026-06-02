package com.leonardtrinh.supportsaas.knowledgebase;

import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.billing.QuotaService;
import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.document.DocumentStatus;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private QuotaService quotaService;

    private KnowledgeBaseServiceImpl knowledgeBaseService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseServiceImpl(
                knowledgeBaseRepository, documentRepository, quotaService);
    }

    // --- createForBusiness tests ---

    @Test
    @DisplayName("createForBusiness_whenUnderQuota_savesKnowledgeBase")
    void createForBusiness_whenUnderQuota_savesKnowledgeBase() {
        when(knowledgeBaseRepository.countByBusinessId(TENANT_ID)).thenReturn(0L);
        doNothing().when(quotaService).checkKnowledgeBaseQuota(TENANT_ID, 0L);
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase result = knowledgeBaseService.createForBusiness(TENANT_ID);

        verify(quotaService).checkKnowledgeBaseQuota(TENANT_ID, 0L);
        verify(knowledgeBaseRepository).save(any(KnowledgeBase.class));
        assertThat(result.getBusinessId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("createForBusiness_whenQuotaExceeded_throwsQuotaExceededException")
    void createForBusiness_whenQuotaExceeded_throwsQuotaExceededException() {
        when(knowledgeBaseRepository.countByBusinessId(TENANT_ID)).thenReturn(1L);
        doThrow(new QuotaExceededException("knowledge_bases", 1, 1))
                .when(quotaService).checkKnowledgeBaseQuota(TENANT_ID, 1L);

        assertThatThrownBy(() -> knowledgeBaseService.createForBusiness(TENANT_ID))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("knowledge_bases");

        verify(knowledgeBaseRepository, never()).save(any());
    }

    // --- getForCurrentTenant tests ---

    @Test
    @DisplayName("getForCurrentTenant_whenKbExists_returnsResponse")
    void getForCurrentTenant_whenKbExists_returnsResponse() {
        KnowledgeBase kb = new KnowledgeBase();
        ReflectionTestUtils.setField(kb, "id", KB_ID);
        kb.setBusinessId(TENANT_ID);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(knowledgeBaseRepository.findByBusinessId(TENANT_ID)).thenReturn(Optional.of(kb));
            when(documentRepository.countByKnowledgeBaseId(KB_ID)).thenReturn(3L);
            when(documentRepository.countByKnowledgeBaseIdAndStatus(KB_ID, DocumentStatus.READY)).thenReturn(2L);

            KnowledgeBaseResponse response = knowledgeBaseService.getForCurrentTenant();

            assertThat(response.id()).isEqualTo(KB_ID);
            assertThat(response.businessId()).isEqualTo(TENANT_ID);
            assertThat(response.documentCount()).isEqualTo(3L);
            assertThat(response.readyCount()).isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("getForCurrentTenant_whenKbNotFound_throwsKnowledgeBaseNotFoundException")
    void getForCurrentTenant_whenKbNotFound_throwsKnowledgeBaseNotFoundException() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getTenantId).thenReturn(TENANT_ID);
            when(knowledgeBaseRepository.findByBusinessId(TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> knowledgeBaseService.getForCurrentTenant())
                    .isInstanceOf(KnowledgeBaseNotFoundException.class);
        }
    }
}
