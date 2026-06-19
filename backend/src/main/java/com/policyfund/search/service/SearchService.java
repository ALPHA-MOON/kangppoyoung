package com.policyfund.search.service;

import com.policyfund.search.domain.SearchHistoryEntity;
import com.policyfund.search.domain.SearchHistoryRepository;
import com.policyfund.search.dto.Article;
import com.policyfund.search.dto.SearchHistoryItem;
import com.policyfund.search.dto.SearchResult;
import com.policyfund.search.query.QueryAnalyzer;
import com.policyfund.search.query.QueryPlan;
import com.policyfund.search.query.RefineDecision;
import com.policyfund.search.query.RetrievalRefiner;
import com.policyfund.search.retrieval.CandidateMerge;
import com.policyfund.search.retrieval.RetrievalPort;
import com.policyfund.search.synth.AnswerSynthesizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SearchService {

    // 2-hop 병합 시 섹션당/전체 후보 한도(과대표집 섹션의 재범람 차단).
    private static final int MERGE_MAX_PER_SECTION = 18;
    private static final int MERGE_MAX_CANDIDATES = 90;

    private final QueryAnalyzer queryAnalyzer;
    private final RetrievalRefiner refiner;
    private final RetrievalPort retrieval;
    private final AnswerSynthesizer synthesizer;
    private final SearchHistoryRepository history;

    public SearchService(QueryAnalyzer queryAnalyzer, RetrievalRefiner refiner, RetrievalPort retrieval,
                         AnswerSynthesizer synthesizer, SearchHistoryRepository history) {
        this.queryAnalyzer = queryAnalyzer;
        this.refiner = refiner;
        this.retrieval = retrieval;
        this.synthesizer = synthesizer;
        this.history = history;
    }

    @Transactional
    public SearchResult search(String query) {
        // 바운드 2-hop: 1) 의도 분석 → 2) 1차 검색 → (절차/목록이면 커버리지 점검 후 부족하면 1회 타깃 재검색)
        //              → 3) 분석+원 질의로 답변 합성.
        QueryPlan plan = queryAnalyzer.analyze(query);
        List<Article> candidates = retrieval.search(plan.retrievalQuery(query));
        if (isProcedureLike(plan)) {
            RefineDecision decision = refiner.evaluate(query, plan, candidates);
            if (decision.needsMore()
                    && decision.followUpQuery() != null && !decision.followUpQuery().isBlank()) {
                List<Article> more = retrieval.search(decision.followUpQuery());
                candidates = CandidateMerge.mergeAndCap(candidates, more,
                        MERGE_MAX_PER_SECTION, MERGE_MAX_CANDIDATES);
            }
        }
        SearchResult result = synthesizer.synthesize(query, plan, candidates);
        history.save(new SearchHistoryEntity(query, result.answer(), result, Instant.now()));
        return result;
    }

    /** 전체 항목 회수가 중요한 절차·목록·순서 질의에만 hop-2 점검을 가동(비용 절감 게이트). */
    private static boolean isProcedureLike(QueryPlan plan) {
        String type = plan.answerType();
        return type != null && (type.contains("절차") || type.contains("목록") || type.contains("순서"));
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryItem> history(int page, int size) {
        return history.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).stream()
                .map(h -> new SearchHistoryItem(
                        String.valueOf(h.getId()), h.getQuery(), h.getCreatedAt(), h.getResultJson()))
                .toList();
    }
}
