package com.policyfund.notices.dto;

/** 버전 간 변경 단위. type: same(동일) / add(추가, 초록) / del(삭제, 빨강). */
public record DiffBlock(String type, ContentBlock block) {}
