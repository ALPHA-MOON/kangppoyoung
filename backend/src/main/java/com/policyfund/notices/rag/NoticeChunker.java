package com.policyfund.notices.rag;

import java.nio.file.Path;

/**
 * 공고 PDF → RAG 청크(chunks.jsonl) 변환 추상화.
 * 운영 구현({@link PipelineNoticeChunker})은 번들된 python pipeline 을 서브프로세스로 실행한다.
 * 테스트에서는 python 없이 동작하는 스텁으로 대체한다.
 */
public interface NoticeChunker {

    /**
     * PDF 바이트를 청킹해 chunks.jsonl 경로를 돌려준다.
     * fileName 은 청크 메타(file_name)·출처 표기에 쓰이는 표시용 문서명이다(검색 결과 출처가 'input.pdf'
     * 같은 임시명으로 새지 않도록 원본/카테고리 문서명을 넘긴다).
     * 반환 경로의 부모 디렉터리가 곧 청커가 만든 임시 루트이며, 호출측({@link RagReindexService})이
     * 적재 후 그 디렉터리를 통째로 정리한다.
     */
    Path chunk(byte[] pdf, String fileName);
}
