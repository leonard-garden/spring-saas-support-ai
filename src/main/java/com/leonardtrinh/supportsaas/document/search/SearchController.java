package com.leonardtrinh.supportsaas.document.search;

import com.leonardtrinh.supportsaas.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb/search")
public class SearchController {

    private final HybridSearchService hybridSearchService;

    public SearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<SearchResult>>> search(
            @RequestBody @Valid SearchRequest request) {
        List<SearchResult> results = hybridSearchService.search(request.getQuery(), request.getTopK());
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
