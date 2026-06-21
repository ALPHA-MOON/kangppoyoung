package com.policyfund.search.ingest;

import com.policyfund.search.embedding.ChunkEmbeddingEntity;
import com.policyfund.search.embedding.ChunkEmbeddingRepository;
import com.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개정본 재색인의 핵심: 카테고리 '최신본'만 유지(이전 버전 청크 교체). hash 임베딩으로 Docker MySQL 에 적재.
 * (전체 pipeline 서브프로세스는 python 의존이라 여기선 chunks.jsonl 을 직접 만들어 적재 경로만 검증한다.)
 */
class ChunkReindexIntegrationTest extends AbstractIntegrationTest {

    @Autowired ChunkIngestService ingestService;
    @Autowired ChunkEmbeddingRepository repository;

    @Test
    void replaceCategory_keepsOnlyLatestVersion() throws Exception {
        // v1: 2개 청크 적재
        Path v1 = Files.createTempFile("reidx-v1", ".jsonl");
        Files.writeString(v1,
                "{\"chunk_id\":\"c_reidx_a1\",\"document_id\":\"d_reidx_v1\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제1조 목적 v1\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n"
                        + "{\"chunk_id\":\"c_reidx_a2\",\"document_id\":\"d_reidx_v1\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제2조 정의 v1\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n");
        List<ChunkEmbeddingEntity> e1 = ingestService.readEntities(v1, "reidxcat");
        ingestService.replaceCategory("reidxcat", e1);

        assertThat(repository.countByCategory("reidxcat")).isEqualTo(2);
        assertThat(repository.findById("c_reidx_a1")).isPresent();
        assertThat(repository.findById("c_reidx_a1").orElseThrow().getCategory()).isEqualTo("reidxcat");

        // v2: 1개 청크 → 이전 버전(v1)은 통째로 교체되어 최신본만 남는다
        Path v2 = Files.createTempFile("reidx-v2", ".jsonl");
        Files.writeString(v2,
                "{\"chunk_id\":\"c_reidx_b1\",\"document_id\":\"d_reidx_v2\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제1조 목적 v2 개정\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n");
        List<ChunkEmbeddingEntity> e2 = ingestService.readEntities(v2, "reidxcat");
        ingestService.replaceCategory("reidxcat", e2);

        assertThat(repository.countByCategory("reidxcat")).isEqualTo(1);
        assertThat(repository.findById("c_reidx_a1")).isEmpty(); // 이전 버전 청크 삭제됨
        assertThat(repository.findById("c_reidx_a2")).isEmpty();
        assertThat(repository.findById("c_reidx_b1")).isPresent();
    }
}
