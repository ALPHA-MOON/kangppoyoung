package com.policyfund.rankings.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RankingCacheRepository extends JpaRepository<RankingCacheEntity, Long> {
    List<RankingCacheEntity> findByPeriodOrderBySearchCountDescViewCountDesc(String period);
    void deleteByPeriod(String period);
    boolean existsByPeriod(String period);
}
