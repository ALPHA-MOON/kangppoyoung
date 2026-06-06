import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  NOTICES,
  diffBlocks,
  type ContentBlock,
  type DiffBlock,
  type NoticeVersion,
} from '../data/mock'
import { Card, PageHeader, TypeBadge } from '../components/ui'

type CatKey = 'regulation' | 'reference'

export default function PolicyNotice() {
  const params = useParams()
  const category = (params.category as CatKey) ?? 'regulation'
  const cat = NOTICES[category]

  // 버전 목록 (날짜 내림차순). 등록 시 새 버전이 앞에 추가됨.
  const [versions, setVersions] = useState<NoticeVersion[]>(cat.versions)
  const [selected, setSelected] = useState(0)
  const [showRegister, setShowRegister] = useState(false)

  // 카테고리(라우트) 변경 시 상태 초기화
  useEffect(() => {
    setVersions(NOTICES[category].versions)
    setSelected(0)
    setShowRegister(false)
  }, [category])

  const current = versions[selected]
  const previous = versions[selected + 1] // 바로 전(더 오래된) 버전
  const diff = useMemo(
    () => (previous ? diffBlocks(previous.blocks, current.blocks) : null),
    [previous, current],
  )

  function handleRegister(date: string, blocks: ContentBlock[]) {
    if (!date || blocks.length === 0) return
    const nextNo = versions.length + 1
    const newVersion: NoticeVersion = { version: `v${nextNo}`, date, blocks }
    setVersions((prev) => [newVersion, ...prev])
    setSelected(0) // 최신본 선택
    setShowRegister(false)
  }

  return (
    <div>
      <PageHeader
        title={`정책 자금 공고 · ${cat.label}`}
        desc={`${cat.docTitle} — 개정 이력을 버전별로 조회하고, 바로 전 버전과의 변경 사항을 확인합니다.`}
      />

      {/* 툴바: 오른쪽 상단 버전 드랍박스 + 등록 버튼 */}
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <TypeBadge type={category === 'regulation' ? '규정' : '절차'} />
          <span className="text-sm font-semibold text-slate-700">{cat.docTitle}</span>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selected}
            onChange={(e) => setSelected(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-slate-900"
          >
            {versions.map((v, i) => (
              <option key={v.version + v.date} value={i}>
                {v.date} · {v.version}
                {i === 0 ? ' (최신)' : ''}
              </option>
            ))}
          </select>
          <button
            onClick={() => setShowRegister(true)}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
          >
            + 개정본 등록
          </button>
        </div>
      </div>

      {/* 본문 + diff */}
      <Card>
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm">
            <span className="font-bold text-slate-900">{current.version}</span>
            <span className="text-slate-400">시행일 {current.date}</span>
          </div>
          {previous ? (
            <span className="text-xs text-slate-400">
              비교 기준: 바로 전 버전 {previous.version} ({previous.date})
            </span>
          ) : (
            <span className="text-xs text-slate-400">최초 등록본 · 비교 대상 없음</span>
          )}
        </div>

        {previous && (
          <div className="mb-3 flex gap-3 text-xs">
            <span className="inline-flex items-center gap-1 text-emerald-600">
              <span className="inline-block h-3 w-3 rounded-sm bg-emerald-200" /> 추가
            </span>
            <span className="inline-flex items-center gap-1 text-rose-600">
              <span className="inline-block h-3 w-3 rounded-sm bg-rose-200" /> 삭제
            </span>
          </div>
        )}

        <div className="space-y-1">
          {diff
            ? diff.map((d, i) => <DiffRow key={i} diff={d} />)
            : current.blocks.map((b, i) => <BlockView key={i} block={b} />)}
        </div>
      </Card>

      {showRegister && (
        <RegisterModal
          docTitle={cat.docTitle}
          previous={versions[0]}
          onClose={() => setShowRegister(false)}
          onSubmit={handleRegister}
        />
      )}
    </div>
  )
}

/** 본문 블록 1개 렌더 (텍스트/이미지) */
function BlockView({ block }: { block: ContentBlock }) {
  if (block.type === 'image') {
    return (
      <div className="px-2 py-1">
        <img
          src={block.src}
          alt={block.name ?? '이미지'}
          className="max-h-64 rounded-lg border border-slate-200"
        />
        {block.name && <div className="mt-1 text-xs text-slate-400">{block.name}</div>}
      </div>
    )
  }
  return <div className="px-2 py-1 text-sm leading-relaxed text-slate-700">{block.text}</div>
}

function DiffRow({ diff }: { diff: DiffBlock }) {
  const { type, block } = diff
  if (type === 'same') {
    return (
      <div className="px-2 py-1">
        <BlockView block={block} />
      </div>
    )
  }
  const isAdd = type === 'add'
  return (
    <div
      className={`flex gap-2 rounded px-2 py-1 ${
        isAdd ? 'bg-emerald-50' : 'bg-rose-50'
      }`}
    >
      <span
        className={`select-none font-mono text-sm ${isAdd ? 'text-emerald-500' : 'text-rose-400'}`}
      >
        {isAdd ? '+' : '−'}
      </span>
      {block.type === 'image' ? (
        <div>
          <img
            src={block.src}
            alt={block.name ?? '이미지'}
            className={`max-h-56 rounded-lg border ${
              isAdd ? 'border-emerald-300' : 'border-rose-300 opacity-70'
            }`}
          />
          {block.name && (
            <div className={`mt-1 text-xs ${isAdd ? 'text-emerald-600' : 'text-rose-500'}`}>
              {block.name}
            </div>
          )}
        </div>
      ) : (
        <span
          className={`text-sm leading-relaxed ${
            isAdd ? 'text-emerald-800' : 'text-rose-700 line-through'
          }`}
        >
          {block.text}
        </span>
      )}
    </div>
  )
}

// ── 등록 모달: PDF 업로드 → 전처리 → 검토·승인 ──

type EditorBlock = ContentBlock & { id: number }

let blockId = 0

type Step = 'upload' | 'processing' | 'review'

const PROCESS_STAGES = ['텍스트 추출', '표·도표 인식', '텍스트 정규화']

/** 전처리 예시용 추출 도표 이미지 */
const extractedDiagram =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="360" height="110">' +
      '<rect width="360" height="110" fill="#f1f5f9" rx="8"/>' +
      '<text x="180" y="60" font-size="14" text-anchor="middle" fill="#475569">PDF에서 추출된 도표 (전처리됨)</text>' +
      '</svg>',
  )

/** PDF 전처리 결과(목업): 텍스트 + 도표 이미지 블록으로 정규화 */
function buildPreprocessResult(fileName: string): ContentBlock[] {
  return [
    { type: 'text', text: `제1조(목적) 이 문서는 ${fileName} 개정 내용을 반영한다.` },
    { type: 'text', text: '제2조(신청자격) 신청 자격은 중소기업 및 소상공인으로 한다.' },
    { type: 'text', text: '제8조(대출한도) 대출 한도는 자기자본의 200% 범위 내에서 산정한다.' },
    { type: 'image', src: extractedDiagram, name: '표1_심사절차(추출).png' },
    { type: 'text', text: '제12조(서류제출) 제출 서류는 접수 마감 후 영업일 5일 이내 제출한다.' },
  ]
}

function StepIndicator({ step }: { step: Step }) {
  const items: { key: Step; label: string }[] = [
    { key: 'upload', label: 'PDF 업로드' },
    { key: 'processing', label: '전처리' },
    { key: 'review', label: '검토·승인' },
  ]
  const order: Step[] = ['upload', 'processing', 'review']
  const cur = order.indexOf(step)
  return (
    <div className="flex items-center gap-1">
      {items.map((it, i) => (
        <div key={it.key} className="flex items-center">
          <span
            className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
              i <= cur ? 'bg-slate-900 text-white' : 'bg-slate-200 text-slate-400'
            }`}
          >
            {i + 1}
          </span>
          <span
            className={`ml-1.5 text-xs ${i <= cur ? 'font-semibold text-slate-700' : 'text-slate-400'}`}
          >
            {it.label}
          </span>
          {i < items.length - 1 && <span className="mx-2 text-slate-300">›</span>}
        </div>
      ))}
    </div>
  )
}

const keyOf = (b: ContentBlock): string => (b.type === 'text' ? `T:${b.text}` : `I:${b.src}`)

/** 검토 화면 왼쪽(이전 버전) 읽기 전용 블록 — 삭제된 항목은 빨강 */
function ReviewBlock({ block, tone }: { block: ContentBlock; tone: 'same' | 'del' }) {
  const del = tone === 'del'
  if (block.type === 'image') {
    return (
      <div className={`rounded-lg border p-2 ${del ? 'border-rose-200 bg-rose-50' : 'border-slate-100'}`}>
        <img
          src={block.src}
          alt={block.name ?? '이미지'}
          className={`max-h-40 rounded border border-slate-200 ${del ? 'opacity-70' : ''}`}
        />
        {block.name && <div className="mt-1 text-xs text-slate-400">🖼 {block.name}</div>}
      </div>
    )
  }
  return (
    <div
      className={`rounded-lg border p-2 text-sm leading-relaxed ${
        del ? 'border-rose-200 bg-rose-50 text-rose-700 line-through' : 'border-slate-100 text-slate-700'
      }`}
    >
      {block.text}
    </div>
  )
}

function RegisterModal({
  docTitle,
  previous,
  onClose,
  onSubmit,
}: {
  docTitle: string
  previous?: NoticeVersion
  onClose: () => void
  onSubmit: (date: string, blocks: ContentBlock[]) => void
}) {
  const [step, setStep] = useState<Step>('upload')
  const [fileName, setFileName] = useState('')
  const [procStage, setProcStage] = useState(0)
  const [date, setDate] = useState('')
  const [blocks, setBlocks] = useState<EditorBlock[]>([])
  const fileRef = useRef<HTMLInputElement>(null)

  // 전처리 진행 시뮬레이션 → 완료 시 검토 화면으로 결과 전달
  useEffect(() => {
    if (step !== 'processing') return
    setProcStage(0)
    let stage = 0
    const id = setInterval(() => {
      stage += 1
      setProcStage(stage)
      if (stage >= PROCESS_STAGES.length) {
        clearInterval(id)
        setBlocks(buildPreprocessResult(fileName).map((b) => ({ ...b, id: blockId++ })))
        setStep('review')
      }
    }, 600)
    return () => clearInterval(id)
  }, [step, fileName])

  function pickPdf(files: FileList | null) {
    const f = files?.[0]
    if (!f) return
    setFileName(f.name)
    setStep('processing')
  }

  function addText() {
    setBlocks((prev) => [...prev, { id: blockId++, type: 'text', text: '' }])
  }

  function addImages(files: FileList | null) {
    if (!files) return
    Array.from(files).forEach((file) => {
      const reader = new FileReader()
      reader.onload = () => {
        setBlocks((prev) => [
          ...prev,
          { id: blockId++, type: 'image', src: String(reader.result), name: file.name },
        ])
      }
      reader.readAsDataURL(file)
    })
  }

  function updateText(id: number, text: string) {
    setBlocks((prev) => prev.map((b) => (b.id === id && b.type === 'text' ? { ...b, text } : b)))
  }

  function remove(id: number) {
    setBlocks((prev) => prev.filter((b) => b.id !== id))
  }

  function move(id: number, dir: -1 | 1) {
    setBlocks((prev) => {
      const idx = prev.findIndex((b) => b.id === id)
      const to = idx + dir
      if (idx < 0 || to < 0 || to >= prev.length) return prev
      const next = [...prev]
      ;[next[idx], next[to]] = [next[to], next[idx]]
      return next
    })
  }

  function handleApprove() {
    const clean: ContentBlock[] = blocks
      .map((b): ContentBlock =>
        b.type === 'text'
          ? { type: 'text', text: b.text.trim() }
          : { type: 'image', src: b.src, name: b.name },
      )
      .filter((b) => (b.type === 'text' ? b.text.length > 0 : true))
    onSubmit(date, clean)
  }

  const hasContent = blocks.some((b) => (b.type === 'text' ? b.text.trim().length > 0 : true))
  const imageRef = useRef<HTMLInputElement>(null)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        className={`flex max-h-[90vh] w-full flex-col rounded-xl bg-white shadow-xl ${
          step === 'review' ? 'max-w-5xl' : 'max-w-2xl'
        }`}
      >
        <div className="border-b border-slate-100 px-6 py-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-slate-900">개정본 등록</h2>
            <StepIndicator step={step} />
          </div>
          <p className="mt-1 text-sm text-slate-500">{docTitle}</p>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-4">
          {/* STEP 1. PDF 업로드 */}
          {step === 'upload' && (
            <div>
              <button
                onClick={() => fileRef.current?.click()}
                className="flex w-full flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 p-10 text-slate-400 hover:border-slate-900 hover:text-slate-600"
              >
                <span className="text-3xl">📄</span>
                <span className="mt-2 text-sm font-semibold">개정 PDF 파일 선택</span>
                <span className="mt-0.5 text-xs">PDF를 업로드하면 자동으로 전처리됩니다.</span>
              </button>
              <input
                ref={fileRef}
                type="file"
                accept="application/pdf,.pdf"
                hidden
                onChange={(e) => {
                  pickPdf(e.target.files)
                  e.target.value = ''
                }}
              />
            </div>
          )}

          {/* STEP 2. 전처리 진행 */}
          {step === 'processing' && (
            <div className="py-6">
              <p className="mb-4 text-center text-sm text-slate-600">
                <span className="font-semibold">{fileName}</span> 전처리 중…
              </p>
              <div className="mx-auto max-w-sm space-y-2">
                {PROCESS_STAGES.map((label, i) => (
                  <div
                    key={label}
                    className="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 text-sm"
                  >
                    <span
                      className={`flex h-5 w-5 items-center justify-center rounded-full text-xs ${
                        i < procStage
                          ? 'bg-emerald-500 text-white'
                          : i === procStage
                            ? 'bg-slate-900 text-white'
                            : 'bg-slate-200 text-slate-400'
                      }`}
                    >
                      {i < procStage ? '✓' : i + 1}
                    </span>
                    <span className={i <= procStage ? 'text-slate-800' : 'text-slate-400'}>
                      {label}
                      {i === procStage ? ' …' : ''}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* STEP 3. 검토 · 승인 — 이전 버전 ↔ 갱신본 나란히 비교(diff) */}
          {step === 'review' &&
            (() => {
              const prevBlocks = previous?.blocks ?? []
              const prevKeys = new Set(prevBlocks.map(keyOf))
              const newKeys = new Set(blocks.map((b) => keyOf(b)))
              return (
                <div className="space-y-4">
                  <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
                    ✅ <b>{fileName}</b> 전처리 완료. 이전 버전과 갱신본을 비교해 확인하고, 필요 시
                    수정한 뒤 등록을 승인하세요.
                  </div>

                  <div className="flex flex-wrap items-end justify-between gap-3">
                    <div className="flex items-center gap-3 text-xs">
                      <span className="inline-flex items-center gap-1 text-emerald-600">
                        <span className="inline-block h-3 w-3 rounded-sm bg-emerald-200" /> 추가
                      </span>
                      <span className="inline-flex items-center gap-1 text-rose-600">
                        <span className="inline-block h-3 w-3 rounded-sm bg-rose-200" /> 삭제
                      </span>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-semibold text-slate-500">
                        시행일
                      </label>
                      <input
                        type="date"
                        value={date}
                        onChange={(e) => setDate(e.target.value)}
                        className="rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
                      />
                    </div>
                  </div>

                  <div className="grid gap-3 md:grid-cols-2">
                    {/* 왼쪽: 이전 버전 (읽기 전용, 삭제=빨강) */}
                    <div className="rounded-lg border border-slate-200">
                      <div className="border-b border-slate-100 bg-slate-50 px-3 py-2 text-xs font-bold text-slate-500">
                        {previous
                          ? `이전 버전 · ${previous.version} (${previous.date})`
                          : '이전 버전 없음 (최초 등록)'}
                      </div>
                      <div className="space-y-1 p-3">
                        {prevBlocks.length === 0 && (
                          <p className="py-6 text-center text-xs text-slate-400">
                            비교할 이전 버전이 없습니다.
                          </p>
                        )}
                        {prevBlocks.map((b, i) => {
                          const removed = !newKeys.has(keyOf(b))
                          return (
                            <ReviewBlock
                              key={i}
                              block={b}
                              tone={removed ? 'del' : 'same'}
                            />
                          )
                        })}
                      </div>
                    </div>

                    {/* 오른쪽: 갱신본 (편집 가능, 추가=초록) */}
                    <div className="rounded-lg border border-slate-200">
                      <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-3 py-2">
                        <span className="text-xs font-bold text-slate-500">갱신본 (등록 예정)</span>
                        <div className="flex gap-1">
                          <button
                            onClick={addText}
                            className="rounded border border-slate-300 px-2 py-0.5 text-xs font-semibold text-slate-600 hover:bg-white"
                          >
                            + 텍스트
                          </button>
                          <button
                            onClick={() => imageRef.current?.click()}
                            className="rounded border border-slate-300 px-2 py-0.5 text-xs font-semibold text-slate-600 hover:bg-white"
                          >
                            + 이미지
                          </button>
                          <input
                            ref={imageRef}
                            type="file"
                            accept="image/*"
                            multiple
                            hidden
                            onChange={(e) => {
                              addImages(e.target.files)
                              e.target.value = ''
                            }}
                          />
                        </div>
                      </div>
                      <div className="space-y-2 p-3">
                        {blocks.length === 0 && (
                          <p className="py-6 text-center text-xs text-slate-400">
                            내용이 없습니다. 텍스트/이미지를 추가하세요.
                          </p>
                        )}
                        {blocks.map((b) => {
                          const added = !prevKeys.has(keyOf(b))
                          return (
                            <div
                              key={b.id}
                              className={`flex items-start gap-2 rounded-lg border p-2 ${
                                added ? 'border-emerald-200 bg-emerald-50' : 'border-slate-100 bg-white'
                              }`}
                            >
                              <div className="flex flex-col gap-0.5 pt-1">
                                <button
                                  onClick={() => move(b.id, -1)}
                                  className="h-5 w-5 rounded text-xs text-slate-400 hover:bg-slate-100"
                                  aria-label="위로"
                                >
                                  ↑
                                </button>
                                <button
                                  onClick={() => move(b.id, 1)}
                                  className="h-5 w-5 rounded text-xs text-slate-400 hover:bg-slate-100"
                                  aria-label="아래로"
                                >
                                  ↓
                                </button>
                              </div>
                              <div className="flex-1">
                                {b.type === 'text' ? (
                                  <textarea
                                    value={b.text}
                                    onChange={(e) => updateText(b.id, e.target.value)}
                                    rows={2}
                                    placeholder="조항/문단 내용을 입력하세요"
                                    className="w-full resize-y rounded border border-slate-200 bg-white px-2 py-1.5 text-sm outline-none focus:border-slate-900"
                                  />
                                ) : (
                                  <div>
                                    <img
                                      src={b.src}
                                      alt={b.name ?? '이미지'}
                                      className="max-h-40 rounded border border-slate-200"
                                    />
                                    <div className="mt-1 text-xs text-slate-400">🖼 {b.name}</div>
                                  </div>
                                )}
                              </div>
                              <button
                                onClick={() => remove(b.id)}
                                className="rounded px-2 py-1 text-xs text-slate-400 hover:bg-rose-50 hover:text-rose-500"
                              >
                                삭제
                              </button>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  </div>
                </div>
              )
            })()}
        </div>

        <div className="flex justify-between gap-2 border-t border-slate-100 px-6 py-4">
          <button
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50"
          >
            취소
          </button>
          {step === 'review' && (
            <div className="flex gap-2">
              <button
                onClick={() => setStep('upload')}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50"
              >
                다시 업로드
              </button>
              <button
                onClick={handleApprove}
                disabled={!date || !hasContent}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
              >
                승인 후 등록
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
