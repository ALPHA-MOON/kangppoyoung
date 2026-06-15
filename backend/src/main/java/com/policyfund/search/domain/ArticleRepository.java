package com.policyfund.search.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArticleRepository extends JpaRepository<ArticleEntity, Long> {

    @Query(value = "SELECT * FROM article WHERE MATCH(`text`) AGAINST (:q IN BOOLEAN MODE) LIMIT 20",
            nativeQuery = true)
    List<ArticleEntity> searchFullText(@Param("q") String q);
}
