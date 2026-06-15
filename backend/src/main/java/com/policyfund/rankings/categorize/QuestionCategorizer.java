package com.policyfund.rankings.categorize;

import java.util.List;

/** 기간 내 질의 목록을 유사 질문 카테고리로 묶는다. OpenAI 호출을 격리한다. */
public interface QuestionCategorizer {
    List<CategoryGroup> categorize(List<String> queries);
}
