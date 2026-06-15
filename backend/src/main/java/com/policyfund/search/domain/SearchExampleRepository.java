package com.policyfund.search.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchExampleRepository extends JpaRepository<SearchExampleEntity, Long> {
    List<SearchExampleEntity> findAllByOrderBySlotAsc();
}
