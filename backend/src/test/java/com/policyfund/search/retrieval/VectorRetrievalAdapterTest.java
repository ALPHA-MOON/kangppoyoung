package com.policyfund.search.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyfund.search.dto.Article;
import com.policyfund.search.embedding.ChunkEmbeddingEntity;
import com.policyfund.search.embedding.ChunkEmbeddingRepository;
import com.policyfund.search.embedding.HashingEmbeddingProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 벡터 검색 어댑터 검증(Docker 불필요): 코사인 랭킹·Article 필드 매핑,
 * 부모-문서(small-to-big) 이웃 확장, 후보 상한.
 */
class VectorRetrievalAdapterTest {

    private static final int MAX_CANDIDATES = 90;

    private final HashingEmbeddingProvider embeddingProvider = new HashingEmbeddingProvider();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChunkEmbeddingRepository repository = mock(ChunkEmbeddingRepository.class);
    private final VectorRetrievalAdapter adapter = new VectorRetrievalAdapter(repository, embeddingProvider);

    private ChunkEmbeddingEntity entity(String chunkId, String docId, int seqNo, String fileName,
                                        String contentType, String articleNo, String embeddingText) {
        float[] vec = embeddingProvider.embed(embeddingText);
        String json;
        try {
            json = objectMapper.writeValueAsString(vec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ChunkEmbeddingEntity(chunkId, docId, fileName, contentType, articleNo,
                embeddingText, json, vec.length, seqNo, LocalDateTime.now());
    }

    @Test
    void rankingMatchingChunkFirstAndMapsFields() {
        ChunkEmbeddingEntity match = entity("c1", "d1", 0, "융자공고.pdf", "text", "제3조",
                "정책자금 융자 신청 서류 제출 기한과 절차 안내");
        ChunkEmbeddingEntity unrelated = entity("c2", "d2", 0, "가이드.pdf", "text", "p.5",
                "사무실 비품 구매 및 청소 일정 공지");
        when(repository.findAll()).thenReturn(List.of(unrelated, match));

        List<Article> results = adapter.search("정책자금 융자 신청 서류 제출 기한");

        assertThat(results).isNotEmpty();
        Article top = results.get(0);
        assertThat(top.docId()).isEqualTo("d1");
        assertThat(top.docTitle()).isEqualTo("융자공고.pdf");
        assertThat(top.docType()).isEqualTo("text");
        assertThat(top.articleNo()).isEqualTo("제3조");
        assertThat(top.text()).isEqualTo("정책자금 융자 신청 서류 제출 기한과 절차 안내");
    }

    @Test
    void expandsHitToReadingOrderNeighbors() {
        String query = "정책자금 융자 신청 서류 제출 기한 안내";
        // 절차 문서 dA: seq2 만 질의와 정확히 일치(최상위 히트), 나머지 단계는 질의 토큰을 공유하지 않음.
        List<ChunkEmbeddingEntity> rows = new ArrayList<>();
        rows.add(entity("a0", "dA", 0, "공고.pdf", "text", "절차", "사무 비품 청소 일정 영단계"));
        rows.add(entity("a1", "dA", 1, "공고.pdf", "text", "절차", "사무 비품 청소 일정 일단계"));
        rows.add(entity("a2", "dA", 2, "공고.pdf", "text", "절차", query));
        rows.add(entity("a3", "dA", 3, "공고.pdf", "text", "절차", "사무 비품 청소 일정 삼단계"));
        rows.add(entity("a4", "dA", 4, "공고.pdf", "text", "절차", "사무 비품 청소 일정 사단계"));
        // 질의 토큰을 일부 공유해 이웃(코사인 0)보다 확실히 높게 랭크되는 필러 50개(서로 다른 문서, 이웃 없음).
        for (int i = 0; i < 50; i++) {
            rows.add(entity("f" + i, "f" + i, 0, "f.pdf", "text", "p.1", "정책자금 융자 신청 서류"));
        }
        when(repository.findAll()).thenReturn(rows);

        List<Article> results = adapter.search(query);
        List<String> texts = results.stream().map(Article::text).toList();

        // 최상위 히트(a2)의 reading-order 이웃들이, 자체로는 상위 히트가 아니어도 함께 끌려와야 한다.
        assertThat(texts).contains(query,
                "사무 비품 청소 일정 영단계", "사무 비품 청소 일정 일단계",
                "사무 비품 청소 일정 삼단계", "사무 비품 청소 일정 사단계");
    }

    @Test
    void capsCandidatesAtMax() {
        // 한 문서에 연속 seq 청크를 충분히 많이 두어, 히트 + 이웃 확장이 상한을 넘게 만든다.
        List<ChunkEmbeddingEntity> rows = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            rows.add(entity("c" + i, "dBig", i, "f.pdf", "text", "p.1",
                    "정책자금 융자 신청 서류 제출 기한 안내 " + i));
        }
        when(repository.findAll()).thenReturn(rows);

        List<Article> results = adapter.search("정책자금 융자 신청 서류");

        assertThat(results).hasSizeLessThanOrEqualTo(MAX_CANDIDATES);
    }

    @Test
    void blankQueryReturnsEmpty() {
        assertThat(adapter.search("  ")).isEmpty();
    }
}
