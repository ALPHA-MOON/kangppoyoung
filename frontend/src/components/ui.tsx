import type { ReactNode } from 'react'
import type { Article, DocType } from '../data/mock'

const typeColor: Record<DocType, string> = {
  규정: 'bg-indigo-100 text-indigo-700',
  지침: 'bg-emerald-100 text-emerald-700',
  절차: 'bg-amber-100 text-amber-700',
}

export function TypeBadge({ type }: { type: DocType }) {
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${typeColor[type]}`}>
      {type}
    </span>
  )
}

/** 출처(문서명·조항) 칩 — 모든 답변에 출처를 명시한다는 핵심 규칙 구현 */
export function SourceChip({ article }: { article: Article }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs text-slate-600">
      <TypeBadge type={article.docType} />
      {article.docTitle} · {article.articleNo}
    </span>
  )
}

/** 근거 조항 카드 (원문 그대로 표시) */
export function ArticleCard({ article }: { article: Article }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="mb-1 flex items-center gap-2">
        <TypeBadge type={article.docType} />
        <span className="text-sm font-semibold text-slate-800">{article.docTitle}</span>
        <span className="text-xs text-slate-400">{article.articleNo}</span>
      </div>
      <p className="text-sm leading-relaxed text-slate-600">{article.text}</p>
    </div>
  )
}

export function PageHeader({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="mb-6">
      <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
      <p className="mt-1 text-sm text-slate-500">{desc}</p>
    </div>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-slate-200 bg-white p-5 shadow-sm ${className}`}>
      {children}
    </div>
  )
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <h2 className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-400">{children}</h2>
  )
}
