package com.policyfund.rankings.categorize;

import com.policyfund.search.dto.Article;

import java.util.List;

public record CategoryGroup(String category, String questionExample, List<Article> relatedArticles) {}
