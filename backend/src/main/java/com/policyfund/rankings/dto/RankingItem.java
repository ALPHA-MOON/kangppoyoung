package com.policyfund.rankings.dto;

import com.policyfund.search.dto.Article;

import java.util.List;

public record RankingItem(
        int rank,
        String category,
        String questionExample,
        int searchCount,
        int viewCount,
        String trend,
        List<Article> relatedArticles) {}
