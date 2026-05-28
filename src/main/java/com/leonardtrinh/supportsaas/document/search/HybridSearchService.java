package com.leonardtrinh.supportsaas.document.search;

import java.util.List;

public interface HybridSearchService {
    List<SearchResult> search(String query, int topK);
}
