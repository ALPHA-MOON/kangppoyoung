package com.policyfund.search.service;

import com.policyfund.search.domain.SearchHistoryEntity;
import com.policyfund.search.domain.SearchHistoryRepository;
import com.policyfund.search.dto.Article;
import com.policyfund.search.dto.SearchHistoryItem;
import com.policyfund.search.dto.SearchResult;
import com.policyfund.search.retrieval.RetrievalPort;
import com.policyfund.search.synth.AnswerSynthesizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SearchService {

    private final RetrievalPort retrieval;
    private final AnswerSynthesizer synthesizer;
    private final SearchHistoryRepository history;

    public SearchService(RetrievalPort retrieval, AnswerSynthesizer synthesizer, SearchHistoryRepository history) {
        this.retrieval = retrieval;
        this.synthesizer = synthesizer;
        this.history = history;
    }

    @Transactional
    public SearchResult search(String query) {
        List<Article> candidates = retrieval.search(query);
        SearchResult result = synthesizer.synthesize(query, candidates);
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
