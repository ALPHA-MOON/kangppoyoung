-- 공고 카테고리(regulation/reference)별로 검색 인덱스를 교체할 수 있도록 태그 컬럼 추가.
-- NULL = 공고와 무관한 청크(예: 기초 가이드 등 out/ 부트스트랩 적재분).
-- 값이 있으면 해당 카테고리 '최신본'의 청크 → 개정본 등록 시 카테고리 단위로 통째 교체된다.
ALTER TABLE chunk_embedding ADD COLUMN category VARCHAR(20) NULL;
CREATE INDEX idx_chunk_category ON chunk_embedding (category);
