package com.leonardtrinh.supportsaas.knowledgebase;

import com.leonardtrinh.supportsaas.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeBaseService.getForCurrentTenant()));
    }
}
