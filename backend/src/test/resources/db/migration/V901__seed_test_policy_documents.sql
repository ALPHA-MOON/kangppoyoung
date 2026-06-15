-- 테스트 전용 시드(운영 마이그레이션 아님). article FK(doc_id → policy_document.id) 충족용
-- 부모 문서. 운영 정책문서/조항은 인제스트로 유입되므로 운영 마이그레이션에 넣지 않는다.
INSERT INTO policy_document (id, title, type, updated_at, is_single_source) VALUES
  ('D-100', '지원 규정', '규정', '2026-01-01', TRUE),
  ('D-101', '운영 지침', '지침', '2026-01-01', TRUE);
