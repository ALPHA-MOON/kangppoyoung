package com.policyfund.rankings.service;

import com.policyfund.rankings.categorize.CategoryGroup;
import com.policyfund.rankings.categorize.QuestionCategorizer;
import com.policyfund.rankings.domain.RankingCacheEntity;
import com.policyfund.rankings.domain.RankingCacheRepository;
import com.policyfund.rankings.dto.RankingItem;
import com.policyfund.search.domain.SearchHistoryEntity;
import com.policyfund.search.domain.SearchHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class RankingService {

    private final SearchHistoryRepository history;
    private final QuestionCategorizer categorizer;
    private final RankingCacheRepository cache;

    public RankingService(SearchHistoryRepository history, QuestionCategorizer categorizer,
                          RankingCacheRepository cache) {
        this.history = history;
        this.categorizer = categorizer;
        this.cache = cache;
    }

    @Transactional
    public List<RankingItem> rankings(String period) {
        if (cache.existsByPeriod(period)) {
            return toItems(cache.findByPeriodOrderBySearchCountDescViewCountDesc(period));
        }
        Instant from = Instant.now().minus(days(period), ChronoUnit.DAYS);
        List<String> queries = history.findAll().stream()
                .filter(h -> h.getCreatedAt() != null && h.getCreatedAt().isAfter(from))
                .map(SearchHistoryEntity::getQuery)
                .toList();

        List<CategoryGroup> groups = categorizer.categorize(queries);

        List<RankingCacheEntity> rows = new ArrayList<>();
        Instant now = Instant.now();
        for (CategoryGroup g : groups) {
            long count = queries.stream().filter(q -> relatesTo(q, g)).count();
            int searchCount = (int) Math.max(count, 1);
            rows.add(new RankingCacheEntity(period, g.category(), g.questionExample(),
                    searchCount, searchCount, "same",
                    g.relatedArticles() == null ? List.of() : g.relatedArticles(), now));
        }
        cache.deleteByPeriod(period);
        cache.saveAll(rows);
        return toItems(cache.findByPeriodOrderBySearchCountDescViewCountDesc(period));
    }

    private static boolean relatesTo(String query, CategoryGroup g) {
        if (g.questionExample() != null && (query.contains(g.questionExample()) || g.questionExample().contains(query))) {
            return true;
        }
        return g.category() != null && query.contains(g.category());
    }

    private List<RankingItem> toItems(List<RankingCacheEntity> rows) {
        List<RankingItem> items = new ArrayList<>();
        int rank = 1;
        for (RankingCacheEntity r : rows) {
            items.add(new RankingItem(rank++, r.getCategory(), r.getQuestionExample(),
                    r.getSearchCount(), r.getViewCount(), r.getTrend(),
                    r.getRelatedArticles() == null ? List.of() : r.getRelatedArticles()));
        }
        return items;
    }

    private static long days(String period) {
        if (period != null && period.contains("7")) return 7;
        return 30;
    }
}
