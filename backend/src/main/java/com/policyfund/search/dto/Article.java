package com.policyfund.search.dto;

/** 답변·랭킹 근거 조항(OpenAPI Article). docType 은 규정/지침/절차 문자열. */
public record Article(String docId, String docTitle, String docType, String articleNo, String text) {}
