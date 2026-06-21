package com.policyfund.notices.rag;

import com.policyfund.search.embedding.ChunkEmbeddingEntity;
import com.policyfund.search.ingest.ChunkIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 개정본 등록 시 해당 카테고리의 검색 인덱스를 '최신본'으로 재색인한다.
 * 흐름: pipeline 청킹(서브프로세스) → 임베딩 계산(트랜잭션 밖) → 카테고리 단위 교체(짧은 트랜잭션).
 *
 * 비동기(@Async)로 동작해 등록 응답을 막지 않고, 실패는 로깅만 한다(등록·공고 버전 자체엔 영향 없음).
 * 검색은 카테고리별 최신본만 인덱싱하므로(구버전 혼용 방지) 이전 버전 청크는 교체 시 삭제된다.
 */
@Service
public class RagReindexService {

    private static final Logger log = LoggerFactory.getLogger(RagReindexService.class);

    private final NoticeChunker chunker;
    private final ChunkIngestService ingestService;
    private final boolean enabled;

    public RagReindexService(NoticeChunker chunker,
                             ChunkIngestService ingestService,
                             @Value("${notices.reindex.enabled:true}") boolean enabled) {
        this.chunker = chunker;
        this.ingestService = ingestService;
        this.enabled = enabled;
    }

    @Async("reindexExecutor")
    public void reindex(String category, String fileName, byte[] pdf) {
        if (!enabled) {
            log.debug("RAG 재색인 비활성(notices.reindex.enabled=false) — category={} 건너뜀", category);
            return;
        }
        if (category == null || category.isBlank()) {
            log.warn("RAG 재색인 카테고리 없음 — 건너뜀");
            return;
        }
        if (pdf == null || pdf.length == 0) {
            log.warn("RAG 재색인 입력 PDF 없음 — category={} 건너뜀", category);
            return;
        }
        Path jsonl = null;
        try {
            jsonl = chunker.chunk(pdf, fileName);
            List<ChunkEmbeddingEntity> entities = ingestService.readEntities(jsonl, category);
            // 한 PDF → 하나의 document_id(콘텐츠 해시). out/ 콜드스타트로 적재된 같은 원본의 중복분을 함께 정리.
            String documentId = entities.isEmpty() ? null : entities.get(0).getDocumentId();
            ingestService.replaceCategory(category, documentId, entities);
            log.info("RAG 재색인 완료 — category={}, chunks={}", category, entities.size());
        } catch (Exception e) {
            log.error("RAG 재색인 실패 — category={} (등록은 정상, 검색만 이전 상태 유지)", category, e);
        } finally {
            if (jsonl != null) {
                deleteRecursively(jsonl.getParent());
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패는 무시
                }
            });
        } catch (IOException ignored) {
            // 무시
        }
    }
}
