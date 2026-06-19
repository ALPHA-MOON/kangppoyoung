package com.policyfund.search.service;

import com.policyfund.search.domain.SearchHistoryEntity;
import com.policyfund.search.domain.SearchHistoryRepository;
import com.policyfund.search.dto.Article;
import com.policyfund.search.dto.SearchHistoryItem;
import com.policyfund.search.dto.SearchResult;
import com.policyfund.search.query.QueryAnalyzer;
import com.policyfund.search.query.QueryPlan;
import com.policyfund.search.retrieval.RetrievalPort;
import com.policyfund.search.synth.AnswerSynthesizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SearchService {

    private final QueryAnalyzer queryAnalyzer;
    private final RetrievalPort retrieval;
    private final AnswerSynthesizer synthesizer;
    private final SearchHistoryRepository history;

    public SearchService(QueryAnalyzer queryAnalyzer, RetrievalPort retrieval,
                         AnswerSynthesizer synthesizer, SearchHistoryRepository history) {
        this.queryAnalyzer = queryAnalyzer;
        this.retrieval = retrieval;
        this.synthesizer = synthesizer;
        this.history = history;
    }

    @Transactional
    public SearchResult search(String query) {
        // 1) 질의 분석(의도 구조화) → 2) 확장 질의로 검색(회수율↑) → 3) 분석+원 질의로 답변 합성.
        QueryPlan plan = queryAnalyzer.analyze(query);
        List<Article> candidates = retrieval.search(plan.retrievalQuery(query));
        SearchResult result = synthesizer.synthesize(query, plan, candidates);
        history.save(new SearchHistoryEntity(query, result.answer(), result, Instant.now()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryItem> history(int page, int size) {
        return history.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).stream()
                .map(h -> new SearchHistoryItem(
                        String.valueOf(h.getId()), h.getQuery(), h.getCreatedAt(), h.getResultJson()))
                .toList();
    }
}
