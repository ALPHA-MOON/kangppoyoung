package com.policyfund.notices.preprocess;

import com.policyfund.notices.dto.ContentBlock;

import java.util.List;

/**
 * 전처리 응답. blocks 는 검토용 본문 블록, sourceRef 는 등록 시 재색인 입력으로 쓸
 * 원본 PDF 의 콘텐츠 주소(sha256). 등록 요청에 그대로 실어 보낸다.
 */
public record PreprocessResponse(List<ContentBlock> blocks, String sourceRef) {}
