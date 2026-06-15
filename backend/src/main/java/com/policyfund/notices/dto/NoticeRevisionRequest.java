package com.policyfund.notices.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record NoticeRevisionRequest(
        @NotNull LocalDate effectiveDate,
        @NotEmpty List<ContentBlock> blocks) {}
