package com.leonardtrinh.supportsaas.knowledgebase;

import com.leonardtrinh.supportsaas.billing.QuotaService;
import com.leonardtrinh.supportsaas.document.DocumentRepository;
import com.leonardtrinh.supportsaas.document.DocumentStatus;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final QuotaService quotaService;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository knowledgeBaseRepository,
                                    DocumentRepository documentRepository,
                                    QuotaService quotaService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.quotaService = quotaService;
    }

    @Override
    public KnowledgeBaseResponse getForCurrentTenant() {
        UUID tenantId = TenantContext.getTenantId();
        KnowledgeBase kb = knowledgeBaseRepository.findByBusinessId(tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        long documentCount = documentRepository.countByKnowledgeBaseId(kb.getId());
        long readyCount = documentRepository.countByKnowledgeBaseIdAndStatus(kb.getId(), DocumentStatus.READY);
        return new KnowledgeBaseResponse(kb.getId(), kb.getBusinessId(), documentCount, readyCount, kb.getCreatedAt());
    }

    @Override
    @Transactional
    public KnowledgeBase createForBusiness(UUID businessId) {
        long currentCount = knowledgeBaseRepository.countByBusinessId(businessId);
        quotaService.checkKnowledgeBaseQuota(businessId, currentCount);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setBusinessId(businessId);
        return knowledgeBaseRepository.save(kb);
    }
}
