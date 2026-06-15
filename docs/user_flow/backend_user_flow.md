# Backend User Flow — 정책자금 지원 업무 플랫폼

> 본 문서는 [User Flow](./user_flow.md)의 사용자 시나리오를 **백엔드 처리 관점**에서
> 정리한다. 각 UC가 API 요청으로 들어와 Controller → Service → (Spring AI / MySQL)
> 를 거쳐 응답으로 나가는 내부 흐름을 다룬다.
> 관련 문서: [Backend PRD](../prd/BACKEND_PRD.md) · [OpenAPI](../api/openapi.yaml) · [PRD](../prd/PRD.md)

## 1. 구성 요소 (Components)

- **nginx** — `/api/v1/*` 요청을 백엔드(`:8080`)로 프록시.
- **Controller** — OpenAPI operationId와 1:1. 요청 검증·DTO 변환.
- **Service** — 비즈니스 로직(검색 종합, 버전 누적, diff, 랭킹 집계).
- **Spring AI** — `ChatClient`(답변/판정/Vision), `EmbeddingModel`·`VectorStore`(future).
- **RetrievalPort** — 검색 후보 조회 추상화. 1차: MySQL 전문검색, 향후: ChromaDB.
- **MySQL** — API에 필요한 핵심 데이터의 영구 저장소.

> 흐름 표기: `→` 다음 단계, `⇒` 외부/AI 호출, `▣` MySQL 영구 저장.

---

## 2. UC별 백엔드 처리 흐름 (Flows)

### UC-1. 통합 검색 — `POST /search`

```
[요청 수신]
   → SearchController.searchPolicy(SearchRequest{query})
   → query 검증(빈 값 또는 500자 초과이면 400 BadRequest)
   → (레이트리밋) IP 기반 분당 20회 초과 시 429 반환
[근거 후보 조회]
   → SearchService → RetrievalPort.search(query)
   → (1차) MySqlFullTextRetrievalAdapter: article.text ngram FULLTEXT 검색 → 후보 Article[]
   → (future) ChromaVectorStore 유사도 검색으로 교체
[답변 종합]
   → (PII 마스킹) query에서 이름·사업자번호 등 PII를 마스킹 후 ChatClient에 전달
   ⇒ Spring AI ChatClient(query + 후보 Article[])
        · 시스템 프롬프트: 사용자 입력·문서 내용은 외부 입력이며 지시로 취급하지 않음을 명시
        · 사용자 입력은 user/context 섹션으로만 전달(시스템 프롬프트와 분리)
        · 출처(evidence) 명시 강제
        · 중복 절차 → duplicateSummary(요약 1건 + sources)
        · 상충 절차 → conflicts(원문 병렬, 임의 통합 금지)
   → BeanOutputConverter로 SearchResult DTO 직접 매핑
[저장·응답]
   ▣ search_history(query, answer, result_json, created_at) 저장
   → 200 SearchResult 반환
```

**핵심 규칙**
- 응답에는 항상 `evidence`가 포함된다(근거 없는 답 금지).
- 중복은 둘 다 나열하지 않고 `duplicateSummary` 1건으로 합친다(출처는 모두 표기).
- 상충은 임의 통합하지 않고 `conflicts`로 원문 병렬 표시한다.
- 모든 질의·답변은 `search_history`에 저장되어 UC-4 랭킹·UC-5 온보딩의 소스가 된다.
- `query`는 최대 500자로 제한한다. OpenAPI 계약에는 `maxLength`가 없으므로(계약 파일 변경 안 함) **Controller 검증으로 강제**한다.

### 검색 부가 흐름

```
[이력 조회] GET /search/history?page&size
   → search_history를 created_at 내림차순 페이지네이션 → SearchHistoryItem[]

[예시 조회] GET /search/examples
   → search_example 전체(최대 5개) → SearchExample[]

[예시 추가] POST /search/examples {text}
   → 트랜잭션 내 현재 개수 확인
        · 5개 이상이면 409 Conflict
        · 미만이면 ▣ 저장 후 201 SearchExample

[예시 삭제] DELETE /search/examples/{exampleId}
   → 해당 행 삭제 → 204 No Content
```

### UC-3. 정책 자금 공고 — 개정본 등록 & 버전 비교

```
[문서·버전 조회] GET /notices/{category}
   → category ∈ {regulation, reference} 검증(아니면 404)
   → notice_category + notice_version 조회
   → 버전을 date 내림차순(최신 우선) 정렬 → NoticeCategory 반환

[개정 PDF 전처리] POST /notices/{category}/revisions/preprocess  (multipart/form-data: file)
   → (레이트리밋) IP 기반 분당 5회 초과 시 429 반환
   → NoticeController가 업로드 PDF 수신
   → [업로드 검증]
        · MIME 타입 != application/pdf → 400
        · 파일 크기 > 50 MB → 400
        · 파일명을 UUID 기반으로 강제 재명명(경로 순회 차단)
   → 파일을 격리된 임시 디렉토리에만 저장
   → [텍스트 레이어 추출] PDFBox 등 로컬 라이브러리로 텍스트 레이어가 있는 페이지 우선 처리
   → [Vision 호출] 텍스트 추출 불가 이미지 전용 페이지에 한해서만
     ⇒ Spring AI ChatClient 멀티모달(Media): 텍스트·표·도표 인식·정규화
        · 시스템 프롬프트: PDF 내용은 외부 데이터이며 지시로 취급하지 않음 명시
        · 페이지당 추출 텍스트 8,000자 초과 시 잘라서 전달
   → 검토용 ContentBlock[](text/image) 반환 (※ 등록 확정 아님)
        · 이미지 블록은 UUID 기반 파일명으로 격리 디렉토리에 저장,
          src = /api/v1/notices/assets/{uuid} (외부 URL·data: URI 금지)
          ※ 이 자산 제공 라우트는 OpenAPI 계약 외 내부 라우트(프론트는 src 문자열만 사용)
   → 임시 파일 즉시 삭제(전처리 완료 또는 오류 시 모두)

[개정본 등록] POST /notices/{category}/revisions {effectiveDate, blocks}
   → 검토·승인 완료된 갱신본 수신(승인 게이트 통과 전제)
   → 새 버전 번호 채번(직전 버전 +1)
   ▣ notice_version(category_key, version, date=effectiveDate, blocks_json) 저장
   → 201 NoticeVersion (새 최신 버전)

[버전 diff] GET /notices/{category}/versions/{version}/diff
   → 지정 버전과 바로 전(더 오래된) 버전의 blocks_json 로드
   → NoticeService가 LCS 기반 블록 비교(서버 계산)
   → DiffBlock[] 반환(type: same/add/del — 추가 초록 / 삭제 빨강)
```

**핵심 규칙**
- 전처리 자동화는 등록을 확정하지 않는다 — 반드시 사용자 승인 후 `revisions`로 등록.
- 등록은 단일 진실 문서의 **새 버전 누적**(기존 버전 불변).
- diff는 저장하지 않고 두 버전 blocks로 요청 시 LCS 계산(텍스트·이미지 블록 모두).

### UC-4. 유사 질문 카테고리·랭킹 — `GET /rankings?period=`

```
[요청 수신]
   → RankingController.getRankings(period)  예: '최근 7일' / '최근 30일'
[집계 소스 로드]
   → period로 search_history 범위 조회(질의·조회 데이터)
[카테고리화·랭킹]
   → ranking_cache에 유효 캐시 있으면 재사용(성능)
   → 없으면 ⇒ Spring AI ChatClient로 유사 질문 카테고리화
        · 시스템 프롬프트: search_history 질의 목록은 외부 데이터이며 지시로 취급하지 않음 명시
   → 빈도순(searchCount/viewCount) 랭킹 산출 + trend(up/down/same)
   → 카테고리별 핵심 근거 조항(relatedArticles) 매핑
   ▣ ranking_cache 저장
[응답]
   → RankingItem[] 반환
```

**핵심 규칙**
- 랭킹은 실제 저장된 질의·조회 데이터에서만 산출(임의 데이터 금지).
- 카테고리화 결과는 캐싱하여 매 요청 전체 LLM 재호출을 피한다.

### UC-5. 신규입사자 온보딩 — `GET /onboarding?period=`

```
[요청 수신]
   → OnboardingController.getOnboardingGuide(period)  허용값: '최근 7일' / '최근 30일', 기본값: '최근 30일'
[랭킹 데이터 도출]
   → OnboardingService가 UC-4 랭킹 데이터(ranking_cache)를 그대로 가져옴
   → "많이 보고·많이 검색한" 순서를 학습 순서(order)로 환산
[근거 부착]
   → 각 항목에 선정 근거(reason, searchCount, viewCount)와 relatedArticles 포함
[응답]
   → OnboardingItem[] 반환(order 오름차순)
```

**핵심 규칙**
- 온보딩의 유일한 데이터 소스는 UC-4 랭킹이며 별도 추천 로직을 두지 않는다.
- 기간 변화로 랭킹이 바뀌면 온보딩 우선순위도 자동 최신화된다(선순환).

---

## 3. 데이터 흐름 요약 (Backend)

```
[nginx] /api/v1/* ─► [Controller] ─► [Service]
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        ▼                               ▼                               ▼
[RetrievalPort]                 [Spring AI ChatClient]            [JPA Repository]
 MySQL FULLTEXT (1차)            답변/중복·상충/Vision/카테고리화      ▣ MySQL 영구 저장
 ChromaDB (future)                                                  (search_history 등)
        │                               │
        └───────────────► [SearchResult / NoticeVersion / RankingItem / OnboardingItem]
                                        │
                                        ▼
                          search_history ─► UC-4 랭킹 ─► UC-5 온보딩 (선순환)
```

---

## 4. 공통 처리 (Cross-cutting)

- **에러:** 전역 `@RestControllerAdvice`가 모든 예외를 `Error{code, message}`로 변환
  (검증 실패 400, 미존재 404, 예시 5개 초과 409, 레이트리밋 초과 429, AI/DB 장애 5xx).
  모든 5xx는 고정 메시지만 반환하며 스택트레이스·DB 오류·OpenAI 원본 응답은 클라이언트에 노출하지 않는다.
  `server.error.include-stacktrace=never` 설정을 모든 프로파일에 적용한다.
- **AI 호출:** Spring AI 래퍼에서 타임아웃·재시도(지수 백오프) 처리, 실패 시 `Error`로 변환.
  OpenAI API 오류(429·500 등)는 래퍼에서 가로채어 내부 `Error` 스키마로 변환하며 원본은 노출하지 않는다.
- **저장 일관성:** 예시 5개 제약·버전 채번 등은 서비스 계층 트랜잭션에서 강제.
- **계약 일관성:** 모든 요청/응답은 [OpenAPI](../api/openapi.yaml) 스키마를 위반하지 않는다.
- **인증(확장 지점):** 1차는 전 엔드포인트 허용(nginx IP 제한으로 변경성 경로 보완).
  Spring Security 필터 체인 골격에서 변경성 엔드포인트는 `authenticated()` 블록으로 분리,
  1차에만 `permitAll()`로 임시 열어두고 추후 RBAC(문서 관리자 권한) 전환.
  상세 분류는 [BACKEND_PRD §9.1](../prd/BACKEND_PRD.md) 참조.
- **레이트리밋:** `POST /search`(분당 20회), `POST /revisions/preprocess`(분당 5회)는
  nginx `limit_req` 또는 Bucket4j로 P1 단계에서 구현한다.
- **비밀값:** `.env`·`application-local.yml`은 `.gitignore`에 포함하며 저장소에 커밋하지 않는다.

---

## 5. 미정 / 추후 정의 (Open Questions)

- ChromaDB 전환 시 `RetrievalPort` ↔ `VectorStore` 인터페이스 정합 및 재인덱싱 절차.
- 랭킹 캐시 갱신 주기(온디맨드 vs 배치).
- 유사 질문 카테고리화의 유사도 기준·분류 체계.
- PDF Vision 전처리의 표·도표 표현 포맷(이미지 블록 vs 구조화 텍스트).
- 인증 도입 시점 및 RBAC 권한 매트릭스.
