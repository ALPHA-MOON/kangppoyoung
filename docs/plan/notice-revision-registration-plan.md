# 구현 플랜 — '정책 자금 공고' 개정본 등록 기능(UC-3) 완성

> 기준: [`docs/prd/PRD.md`](../prd/PRD.md) §5 UC-3 + [`docs/user_flow/user_flow.md`](../user_flow/user_flow.md) UC-3.
> 목표: 개정본 등록 플로우(PDF 업로드 → 전처리 → 검토·승인 → 버전 누적 → 버전 diff)를 **실제 백엔드와 연동**해 end-to-end로 동작시킨다.

## 1. 현재 상태 (사전 스캔 결과)

- **백엔드: 완비.** 컨트롤러 5개(`getNotice`/`registerNoticeRevision`/`getNoticeVersionDiff`/`preprocessNoticePdf`/`AssetController`), 서비스(PDFBox 추출 + 이미지 페이지 Vision OCR + sha256 asset 저장, `'v'+n` 채번, LCS 블록 diff), 도메인(`notice_category`/`notice_version`), DTO(record), Flyway(V1 스키마 + V2 카테고리 시드), 통합테스트(`NoticeApiIntegrationTest`/`PreprocessApiIntegrationTest`/`BlockDiffTest`)까지 구현·통과.
- **프론트: UI 완성·백엔드 미연동.** `frontend/src/pages/PolicyNotice.tsx`가 `data/mock.ts`(`NOTICES`·`diffBlocks`)와 600ms `setInterval` 전처리 시뮬레이션·로컬 state 등록으로만 동작. `frontend/src/api/notices.ts`의 4개 클라이언트 함수는 **모두 구현돼 있으나 호출되지 않음**.
- **타입 계약 일치.** `ContentBlock`/`NoticeVersion`/`NoticeCategory`/`NoticeRevisionRequest`/`DiffBlock`(`api/types.ts`)이 백엔드 DTO·mock 타입과 동일 형태. `http` 클라이언트(`api/client.ts`)는 `get/post/postForm`·`ApiError` 완비.

➡ **"완성"의 본질 = `PolicyNotice.tsx`를 mock에서 실제 API 호출로 전환 + 로딩/에러 UX + 빈 버전 상태 처리.** (백엔드 신규 구현 없음.)

## 2. 범위

**In:** `PolicyNotice.tsx` 백엔드 연동(조회·전처리·등록·diff), 로딩/에러/빈상태 UX, 업로드 사전검증(PDF·50MB), 미사용 notices mock 정리, `openapi.yaml`에 자산 라우트 추가(전담 에이전트 위임), 프론트 빌드·기존 백엔드 테스트 무영향 확인.

**Out(이번 범위 밖, §12/로드맵):** UC-4 랭킹·UC-5 온보딩·예시질문 연동, 전처리 비동기(SSE/폴링)·진행률 스트리밍, 등록 audit trail(approved_by 등), RBAC, Myers diff 최적화.

## 3. 작업 항목 (체크리스트)

### 3.1 `PolicyNotice.tsx` — 조회 연동
- [ ] mount/카테고리 변경 시 `getNotice(category)` 호출 → `versions`·`label`·`docTitle` state 세팅. `NOTICES[category]` 제거.
- [ ] 로딩 state(`loading`), 에러 state(`ApiError.message`) 추가. 카테고리 전환 시 초기화.
- [ ] **빈 버전 처리(중요):** 백엔드는 카테고리만 시드, `notice_version`은 초기 비어있을 수 있음 → `versions=[]`면 `current`가 undefined. "등록된 버전이 없습니다 · 개정본을 등록하세요" 빈상태 렌더, 등록 버튼은 항상 노출.

### 3.2 `PolicyNotice.tsx` — diff 연동(서버 계산)
- [ ] 클라이언트 `diffBlocks` 제거. `previous`(= `versions[selected+1]`) 존재 시 `getNoticeVersionDiff(category, current.version)` 호출 → `DiffBlock[]` 렌더. (백엔드가 '바로 전(더 오래된)' 버전 대비 LCS 계산.)
- [ ] 최초 등록본(가장 오래된, `selected === versions.length-1`)은 diff 호출 없이 `current.blocks` 평문 렌더 + "최초 등록본 · 비교 대상 없음".
- [ ] diff 로딩/에러 state(영역 한정). selected/versions 변경 시 재조회(stale 응답 무시 가드).

### 3.3 `RegisterModal` — 전처리 연동
- [ ] `category` prop 추가. `pickPdf`가 `File` 객체를 state로 보관(현재 `fileName`만 저장).
- [ ] `step==='processing'` 진입 시 `setInterval` 시뮬레이션 제거 → `preprocessNoticePdf(category, file)` 호출.
  - 성공: `setBlocks(res.blocks.map(b => ({...b, id: blockId++})))` → `step='review'`.
  - 실패: 에러 메시지 표시 + "다시 업로드"로 복귀(`ApiError.message`).
- [ ] 진행 표시는 단계 시뮬레이션 대신 스피너/"전처리 중…"(백엔드가 단계 milestone 미제공 — 단일 진행 표시). `PROCESS_STAGES`·`buildPreprocessResult`·`extractedDiagram` 더미 제거.
- [ ] 업로드 사전검증: `file.type !== 'application/pdf'` 또는 `file.size > 50MB` 시 업로드 단계에서 즉시 경고(서버 400 왕복 회피, 서버 검증과 이중).

### 3.4 `PolicyNotice.tsx` — 등록 연동
- [ ] `handleRegister`를 async로: `registerNoticeRevision(category, { effectiveDate: date, blocks })` 호출 → 성공 시 `getNotice(category)` 재조회 → `setVersions`·`setSelected(0)`(최신 선택)·모달 닫기.
- [ ] 등록 중 버튼 disabled + 실패 시 에러 표시(모달 유지). 승인 게이트(시행일·내용 필수)는 기존 `disabled={!date || !hasContent}` 유지.

### 3.5 mock 정리
- [ ] `PolicyNotice.tsx`에서 `../data/mock` import 제거(타입은 `../api/types`에서). `NOTICES`·`diffBlocks`·`NoticeVersion`(mock)·`ContentBlock`(mock)·`DiffBlock`(mock)이 다른 화면에서 미사용이면 `data/mock.ts`에서 삭제, 사용 중이면 보존(랭킹/검색 시나리오 등). 사용처 확인 후 결정.

### 3.6 계약·문서
- [ ] `docs/api/openapi.yaml`에 `GET /notices/assets/{id}`(sha256 64-hex path param, 200 image/png, 404) 추가 + 기존 notices 스키마 정합 확인 → **전담 에이전트 `openapi-schema` 위임**(CLAUDE.md 규칙). 3중 동기화(DTO↔openapi↔types.ts) 점검.

### 3.7 검증
- [ ] 프론트 타입체크·빌드: `cd frontend && npm run build`(tsc -b && vite build) green.
- [ ] 백엔드 notices 기존 테스트 무영향 확인(코드 변경은 프론트·문서뿐 — 백엔드 미변경이므로 회귀 없음. 단 ASCII 경로 복사본·Docker 필요 시 환경 제약 기록).
- [ ] (가능 시) 백엔드 기동 후 브라우저 수동 확인: 업로드→전처리→검토→등록→목록·diff 반영.

## 4. 설계 결정·주의

- **diff는 백엔드 계산을 신뢰**(PRD UC-3: "diff는 서버 계산"). 클라이언트 재계산 제거로 단일화.
- **전처리 Vision 의존성:** 이미지 전용 페이지가 있고 `OPENAI_API_KEY`가 없으면 `OpenAiPageVisionExtractor`가 폴백 없이 실패할 수 있음(PRD §9). 텍스트 레이어 PDF는 키 없이도 동작. 실패는 에러 UX로 노출(앱이 죽지 않게).
- **에러 표면화:** 모든 API 호출은 `ApiError`(code/message)를 잡아 사용자 메시지로 표시, 콘솔/흰화면 회귀 방지(검색 화면 패턴 답습).
- **승인 게이트 보존:** 전처리는 등록을 확정하지 않음 — 등록은 '승인 후 등록'에서만.

## 5. 변경 대상 파일
- `frontend/src/pages/PolicyNotice.tsx` (핵심)
- `frontend/src/data/mock.ts` (notices mock 정리, 조건부)
- `docs/api/openapi.yaml` (자산 라우트, openapi-schema 에이전트)
- (백엔드 변경 없음)
