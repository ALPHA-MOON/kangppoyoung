package com.policyfund.search.embedding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** chunk_embedding 저장소. 브루트포스 코사인 검색을 위해 findAll() 로 전량 로드한다. */
public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbeddingEntity, String> {

    List<ChunkEmbeddingEntity> findByDocumentId(String documentId);

    /** 카테고리 최신본 교체(개정본 등록 재색인) 시 이전 버전 청크 일괄 삭제. */
    long deleteByCategory(String category);

    /** 동일 원본(document_id=콘텐츠 해시)의 기존 청크 삭제 — out/ 콜드스타트 적재분 중복 제거용. */
    long deleteByDocumentId(String documentId);

    long countByCategory(String category);
}
