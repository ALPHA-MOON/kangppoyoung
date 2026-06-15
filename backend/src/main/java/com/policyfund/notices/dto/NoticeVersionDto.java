package com.policyfund.notices.dto;

import java.time.LocalDate;
import java.util.List;

public record NoticeVersionDto(String version, LocalDate date, List<ContentBlock> blocks) {}
