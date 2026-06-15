package com.policyfund.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(
        String query,
        String answer,
        List<Article> evidence,
        DuplicateSummary duplicateSummary,
        List<Article> conflicts) {

    public record DuplicateSummary(String summary, List<Article> sources) {}
}
