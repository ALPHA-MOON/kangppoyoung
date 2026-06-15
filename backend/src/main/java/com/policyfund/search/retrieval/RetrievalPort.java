package com.policyfund.search.retrieval;

import com.policyfund.search.dto.Article;

import java.util.List;

/** 질의에 대한 후보 조항을 조회한다. 1차 MySQL FULLTEXT, 추후 ChromaDB 구현체로 교체. */
public interface RetrievalPort {
    List<Article> search(String query);
}
