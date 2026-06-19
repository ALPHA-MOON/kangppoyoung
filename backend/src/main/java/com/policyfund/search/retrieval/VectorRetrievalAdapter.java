package com.policyfund.search.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyfund.search.dto.Article;
import com.policyfund.search.embedding.ChunkEmbeddingEntity;
import com.policyfund.search.embedding.ChunkEmbeddingRepository;
import com.policyfund.search.embedding.EmbeddingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 벡터(코사인 유사도) 기반 검색 어댑터. 부모-문서(small-to-big) 전략을 쓴다.
 * (1) 질의를 임베딩해 chunk_embedding 전량과 코사인 유사도로 정렬, 상위 TOP_K 개를 정밀 히트로 뽑고,
 * (2) 각 히트를 문서 내 reading-order 이웃(seq_no ± NEIGHBOR_WINDOW)으로 확장한다.
 * 절차의 각 단계가 별도 청크로 쪼개져 있어도, 한 단계만 히트하면 그 섹션의 연속 청크가 함께 끌려와
 * 합성 LLM 이 전체 절차·목록을 순서대로 재구성할 수 있다. 히트를 먼저 담아 evidence 는 관련도 순을 유지.
 * 브루트포스(MySQL 8.0 호환, VECTOR 미사용), search.retrieval=vector 일 때 활성화.
 */
@Component
@ConditionalOnProperty(name = "search.retrieval", havingValue = "vector")
public class VectorRetrievalAdapter implements RetrievalPort {

    private static final int TOP_K = 40;            // 코사인 점수 상위 정밀 히트 수
    private static final int NEIGHBOR_WINDOW = 6;   // 각 히트의 reading-order 이웃 확장 폭(±)
    private static final int MAX_CANDIDATES = 90;   // 합성기에 넘길 최대 후보 수(컨텍스트 상한)

    private final ChunkEmbeddingRepository repository;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VectorRetrievalAdapter(ChunkEmbeddingRepository repository, EmbeddingProvider embeddingProvider) {
        this.repository = repository;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public List<Article> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingProvider.embed(query);
        List<ChunkEmbeddingEntity> all = repository.findAll();

        // (문서, seq_no) -> 청크 인메모리 색인: 이웃 확장을 추가 DB 조회 없이 처리.
        Map<String, ChunkEmbeddingEntity> bySeq = new HashMap<>();
        for (ChunkEmbeddingEntity e : all) {
            bySeq.put(seqKey(e.getDocumentId(), e.getSeqNo()), e);
        }

        // 1) 코사인 상위 TOP_K 정밀 히트.
        List<ChunkEmbeddingEntity> hits = all.stream()
                .map(entity -> new Scored(entity, cosine(queryVector, parse(entity.getEmbeddingJson()))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(TOP_K)
                .map(Scored::entity)
                .toList();

        // 2) 히트(관련도 순) 먼저 담고, 그다음 각 히트의 같은 섹션 이웃을 확장(dedup, 상한 적용).
        LinkedHashMap<String, ChunkEmbeddingEntity> picked = new LinkedHashMap<>();
        for (ChunkEmbeddingEntity h : hits) {
            picked.putIfAbsent(h.getChunkId(), h);
        }
        for (ChunkEmbeddingEntity h : hits) {
            if (picked.size() >= MAX_CANDIDATES) {
                break;
            }
            for (int d = 1; d <= NEIGHBOR_WINDOW; d++) {
                addNeighbor(picked, bySeq, h.getDocumentId(), h.getSeqNo() - d);
                addNeighbor(picked, bySeq, h.getDocumentId(), h.getSeqNo() + d);
            }
        }

        return picked.values().stream()
                .limit(MAX_CANDIDATES)
                .map(VectorRetrievalAdapter::toArticle)
                .toList();
    }

    private static void addNeighbor(LinkedHashMap<String, ChunkEmbeddingEntity> picked,
                                    Map<String, ChunkEmbeddingEntity> bySeq, String documentId, int seqNo) {
        if (seqNo < 0) {
            return;
        }
        ChunkEmbeddingEntity neighbor = bySeq.get(seqKey(documentId, seqNo));
        if (neighbor != null) {
            picked.putIfAbsent(neighbor.getChunkId(), neighbor);
        }
    }

    private static String seqKey(String documentId, int seqNo) {
        return documentId + '#' + seqNo;
    }

    private static Article toArticle(ChunkEmbeddingEntity e) {
        return new Article(e.getDocumentId(), e.getFileName(), e.getContentType(),
                e.getArticleNo(), e.getEmbeddingText());
    }

    private float[] parse(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, float[].class);
        } catch (IOException ex) {
            throw new UncheckedIOException("embedding JSON 파싱 실패", ex);
        }
    }

    /** 코사인 유사도. 길이가 다르거나 영벡터면 0. */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Scored(ChunkEmbeddingEntity entity, double score) {}
}
