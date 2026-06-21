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
     * 반환 경로의 부모 임시 디렉터리는 호출측({@link RagReindexService})이 적재 후 정리한다.
     */
    Path chunk(byte[] pdf);
}
