package com.policyfund.search.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistoryEntity, Long> {
    Page<SearchHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
