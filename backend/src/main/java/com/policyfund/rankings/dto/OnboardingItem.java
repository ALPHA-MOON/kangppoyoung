package com.policyfund.rankings.dto;

import com.policyfund.search.dto.Article;

import java.util.List;

public record OnboardingItem(
        int order,
        String category,
        String reason,
        int searchCount,
        int viewCount,
        List<Article> relatedArticles) {}
