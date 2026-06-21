// 정책 자금 공고 · 문서 버전 관리 (OpenAPI tag: notices)
import { http } from './client'
import type {
  AssetRef,
  DiffBlock,
  NoticeCategory,
  NoticeCategoryKey,
  NoticeRevisionRequest,
  NoticeVersion,
  PreprocessResult,
} from './types'

/** GET /notices/{category} — 공고 또는 참고자료 문서와 버전 목록 조회 */
export function getNotice(category: NoticeCategoryKey): Promise<NoticeCategory> {
  return http.get<NoticeCategory>(`/notices/${category}`)
}

/** GET /notices/{category}/versions/{version}/diff — 지정 버전과 바로 전 버전의 블록 단위 변경 내역 조회 */
export function getNoticeVersionDiff(
  category: NoticeCategoryKey,
  version: string,
): Promise<DiffBlock[]> {
  return http.get<DiffBlock[]>(
    `/notices/${category}/versions/${encodeURIComponent(version)}/diff`,
  )
}

/** POST /notices/{category}/revisions — 검토·승인 완료된 개정본을 새 버전으로 확정 등록 */
export function registerNoticeRevision(
  category: NoticeCategoryKey,
  revision: NoticeRevisionRequest,
): Promise<NoticeVersion> {
  return http.post<NoticeVersion>(`/notices/${category}/revisions`, revision)
}

/** POST /notices/{category}/revisions/preprocess — 개정 PDF 업로드 → 검토용 블록 + 재색인용 sourceRef */
export function preprocessNoticePdf(
  category: NoticeCategoryKey,
  file: File,
): Promise<PreprocessResult> {
  const form = new FormData()
  form.append('file', file)
  return http.postForm<PreprocessResult>(
    `/notices/${category}/revisions/preprocess`,
    form,
  )
}

/** POST /notices/assets — 검토 단계에서 수동 추가하는 이미지를 콘텐츠 주소 자산으로 업로드 */
export function uploadNoticeAsset(file: File): Promise<AssetRef> {
  const form = new FormData()
  form.append('file', file)
  return http.postForm<AssetRef>(`/notices/assets`, form)
}
