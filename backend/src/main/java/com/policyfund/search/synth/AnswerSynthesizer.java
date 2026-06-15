package com.policyfund.search.synth;

import com.policyfund.search.dto.Article;
import com.policyfund.search.dto.SearchResult;

import java.util.List;

/** 질의 + 후보 조항 → 답변(출처 명시, 중복 요약/상충 병렬). OpenAI 호출을 격리한다. */
public interface AnswerSynthesizer {
    SearchResult synthesize(String query, List<Article> candidates);
}
