package com.policyfund.notices.service;

import com.policyfund.common.error.ResourceNotFoundException;
import com.policyfund.notices.domain.NoticeCategoryEntity;
import com.policyfund.notices.domain.NoticeCategoryRepository;
import com.policyfund.notices.domain.NoticeVersionEntity;
import com.policyfund.notices.domain.NoticeVersionRepository;
import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.notices.dto.DiffBlock;
import com.policyfund.notices.dto.NoticeCategoryDto;
import com.policyfund.notices.dto.NoticeRevisionRequest;
import com.policyfund.notices.dto.NoticeVersionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoticeService {

    private final NoticeCategoryRepository categories;
    private final NoticeVersionRepository versions;

    public NoticeService(NoticeCategoryRepository categories, NoticeVersionRepository versions) {
        this.categories = categories;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public NoticeCategoryDto getNotice(String category) {
        NoticeCategoryEntity cat = categories.findById(category)
                .orElseThrow(() -> new ResourceNotFoundException("NOTICE_CATEGORY_NOT_FOUND",
                        "공고 카테고리를 찾을 수 없습니다: " + category));

        List<NoticeVersionDto> versionDtos =
                versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                        .map(this::toDto)
                        .toList();

        return new NoticeCategoryDto(cat.getKey(), cat.getLabel(), cat.getDocTitle(), versionDtos);
    }

    @org.springframework.transaction.annotation.Transactional
    public NoticeVersionDto registerRevision(String category, NoticeRevisionRequest request) {
        categories.findById(category)
                .orElseThrow(() -> new ResourceNotFoundException("NOTICE_CATEGORY_NOT_FOUND",
                        "공고 카테고리를 찾을 수 없습니다: " + category));

        int next = versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                .map(com.policyfund.notices.domain.NoticeVersionEntity::getVersion)
                .map(NoticeService::parseVersionNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        var saved = versions.save(new com.policyfund.notices.domain.NoticeVersionEntity(
                category, "v" + next, request.effectiveDate(), request.blocks()));

        return toDto(saved);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<DiffBlock> diff(String category, String version) {
        List<com.policyfund.notices.domain.NoticeVersionEntity> ordered =
                versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                        .sorted(java.util.Comparator
                                .comparing(com.policyfund.notices.domain.NoticeVersionEntity::getDate)
                                .thenComparing(e -> parseVersionNumber(e.getVersion())))
                        .toList();

        int idx = -1;
        for (int k = 0; k < ordered.size(); k++) {
            if (ordered.get(k).getVersion().equals(version)) { idx = k; break; }
        }
        if (idx < 0) {
            throw new ResourceNotFoundException("NOTICE_VERSION_NOT_FOUND",
                    "버전을 찾을 수 없습니다: " + category + "/" + version);
        }

        List<ContentBlock> current = ordered.get(idx).getBlocks();
        List<ContentBlock> previous = idx > 0 ? ordered.get(idx - 1).getBlocks() : List.of();
        return BlockDiff.diff(previous, current);
    }

    private static int parseVersionNumber(String version) {
        try {
            return version.startsWith("v") ? Integer.parseInt(version.substring(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private NoticeVersionDto toDto(NoticeVersionEntity e) {
        return new NoticeVersionDto(e.getVersion(), e.getDate(), e.getBlocks());
    }
}
