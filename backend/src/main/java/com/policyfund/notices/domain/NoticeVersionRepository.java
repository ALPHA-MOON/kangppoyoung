package com.policyfund.notices.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticeVersionRepository extends JpaRepository<NoticeVersionEntity, Long> {

    List<NoticeVersionEntity> findByCategoryKeyOrderByDateDescVersionDesc(String categoryKey);

    Optional<NoticeVersionEntity> findByCategoryKeyAndVersion(String categoryKey, String version);
}
