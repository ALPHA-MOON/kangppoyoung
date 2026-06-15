package com.policyfund.rankings.domain;

import com.policyfund.search.dto.Article;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ranking_cache")
public class RankingCacheEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String period;
    private String category;

    @Column(name = "question_example")
    private String questionExample;

    @Column(name = "search_count")
    private int searchCount;

    @Column(name = "view_count")
    private int viewCount;

    private String trend;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_articles_json", columnDefinition = "json")
    private List<Article> relatedArticles;

    @Column(name = "computed_at")
    private Instant computedAt;

    protected RankingCacheEntity() {}

    public RankingCacheEntity(String period, String category, String questionExample,
                              int searchCount, int viewCount, String trend,
                              List<Article> relatedArticles, Instant computedAt) {
        this.period = period;
        this.category = category;
        this.questionExample = questionExample;
        this.searchCount = searchCount;
        this.viewCount = viewCount;
        this.trend = trend;
        this.relatedArticles = relatedArticles;
        this.computedAt = computedAt;
    }

    public String getCategory() { return category; }
    public String getQuestionExample() { return questionExample; }
    public int getSearchCount() { return searchCount; }
    public int getViewCount() { return viewCount; }
    public String getTrend() { return trend; }
    public List<Article> getRelatedArticles() { return relatedArticles; }
}
