package com.policyfund.notices.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record NoticeRevisionRequest(
        @NotNull LocalDate effectiveDate,
        @NotEmpty List<ContentBlock> blocks,
        // 전처리 응답의 sourceRef(원본 PDF 콘텐츠 주소). 있으면 등록 후 검색 인덱스를 재색인한다. 선택값.
        String sourceRef) {}
