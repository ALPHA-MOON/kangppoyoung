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
 * 개정본 재색인의 핵심: 카테고리 '최신본'만 유지(이전 버전 교체) + 동일 원본의 out/ 콜드스타트 중복 제거.
 * hash 임베딩으로 Docker MySQL 에 적재한다(전체 pipeline 서브프로세스는 python 의존이라 여기선 chunks.jsonl 직접 작성).
 */
class ChunkReindexIntegrationTest extends AbstractIntegrationTest {

    @Autowired ChunkIngestService ingestService;
    @Autowired ChunkEmbeddingRepository repository;

    @Test
    void replaceCategory_keepsOnlyLatestAndDedupesColdStart() throws Exception {
        // out/ 콜드스타트로 적재된 같은 원본(document_id=d_reidx_v1)의 청크 (category=null)
        Path cold = Files.createTempFile("reidx-cold", ".jsonl");
        Files.writeString(cold,
                "{\"chunk_id\":\"c_reidx_cold\",\"document_id\":\"d_reidx_v1\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"콜드스타트 원본 청크\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n");
        ingestService.ingestFile(cold, null);
        assertThat(repository.findById("c_reidx_cold")).isPresent();

        // v1: 2개 청크 (같은 원본 document_id=d_reidx_v1)
        Path v1 = Files.createTempFile("reidx-v1", ".jsonl");
        Files.writeString(v1,
                "{\"chunk_id\":\"c_reidx_a1\",\"document_id\":\"d_reidx_v1\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제1조 목적 v1\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n"
                        + "{\"chunk_id\":\"c_reidx_a2\",\"document_id\":\"d_reidx_v1\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제2조 정의 v1\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n");
        List<ChunkEmbeddingEntity> e1 = ingestService.readEntities(v1, "reidxcat");
        ingestService.replaceCategory("reidxcat", "d_reidx_v1", e1);

        assertThat(repository.countByCategory("reidxcat")).isEqualTo(2);
        // 동일 원본의 out/ 콜드스타트 청크가 제거되어 검색 중복이 없다
        assertThat(repository.findById("c_reidx_cold")).isEmpty();
        assertThat(repository.findById("c_reidx_a1").orElseThrow().getCategory()).isEqualTo("reidxcat");

        // v2: 1개 청크 (다른 원본 document_id=d_reidx_v2) → 이전 버전(카테고리) 통째 교체 → 최신본만
        Path v2 = Files.createTempFile("reidx-v2", ".jsonl");
        Files.writeString(v2,
                "{\"chunk_id\":\"c_reidx_b1\",\"document_id\":\"d_reidx_v2\",\"content_type\":\"paragraph\","
                        + "\"embedding_text\":\"제1조 목적 v2 개정\",\"metadata\":{\"file_name\":\"reidx.pdf\",\"page_no\":1}}\n");
        List<ChunkEmbeddingEntity> e2 = ingestService.readEntities(v2, "reidxcat");
        ingestService.replaceCategory("reidxcat", "d_reidx_v2", e2);

        assertThat(repository.countByCategory("reidxcat")).isEqualTo(1);
        assertThat(repository.findById("c_reidx_a1")).isEmpty(); // 이전 버전 청크 삭제됨
        assertThat(repository.findById("c_reidx_a2")).isEmpty();
        assertThat(repository.findById("c_reidx_b1")).isPresent();
    }
}
