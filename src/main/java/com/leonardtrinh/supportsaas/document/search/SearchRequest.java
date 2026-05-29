package com.leonardtrinh.supportsaas.document.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SearchRequest {

    @NotBlank
    @Size(min = 3, message = "Query must be at least 3 characters")
    private String query;

    @Min(1) @Max(20)
    private int topK = 10;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
