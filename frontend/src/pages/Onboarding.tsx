import { useMemo, useState } from 'react'
import { RANKING_BY_PERIOD, RANKING_PERIODS } from '../data/mock'
import { ArticleCard, Card, PageHeader, SectionLabel } from '../components/ui'

// UC-5: 신규입사자 온보딩.
// 핵심 — 별도 추천 로직이 아니라 UC-4 랭킹(실무자가 많이 보고·많이 검색한 순)을
// 그대로 학습 우선순위로 환산한다.
export default function Onboarding() {
  const [period, setPeriod] = useState(RANKING_PERIODS[0])
  const [done, setDone] = useState<Record<number, boolean>>({})

  // UC-4 랭킹을 학습 우선순위로 변환 (rank 순서 = 학습 순서)
  const curriculum = useMemo(
    () => [...RANKING_BY_PERIOD[period]].sort((a, b) => a.rank - b.rank),
    [period],
  )
  const total = curriculum.length
  const doneCount = curriculum.filter((c) => done[c.rank]).length

  return (
    <div>
      <PageHeader
        title="온보딩 가이드"
        desc="실무자들이 많이 보고·많이 검색한 결과를 학습 우선순위로 환산해 '무엇부터 봐야 하는지'를 안내합니다."
      />

      {/* 데이터 출처 안내 — 임의 추천이 아님을 명시 */}
      <Card className="mb-5 border-l-4 border-l-indigo-400">
        <div className="flex items-center justify-between">
          <div>
            <SectionLabel>학습 우선순위 산출 근거</SectionLabel>
            <p className="text-sm text-slate-700">
              아래 순서는 <b>실무자들의 질문 분석 데이터</b>에서 자동 도출되었습니다. 임의 추천이
              아니라 <b>실제 검색·조회 데이터</b> 기반입니다.
            </p>
          </div>
          <div className="flex gap-1">
            {RANKING_PERIODS.map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium ${
                  period === p
                    ? 'bg-slate-900 text-white'
                    : 'border border-slate-200 text-slate-600'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* 진행률 */}
      <Card className="mb-5">
        <div className="mb-1 flex items-center justify-between text-sm">
          <span className="font-semibold text-slate-800">학습 진행률</span>
          <span className="text-slate-500">
            {doneCount} / {total} 완료
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-emerald-500 transition-all"
            style={{ width: `${(doneCount / total) * 100}%` }}
          />
        </div>
      </Card>

      {/* 커리큘럼 = 랭킹 순서 */}
      <div className="space-y-3">
        {curriculum.map((item, idx) => (
          <Card key={item.rank} className={done[item.rank] ? 'opacity-60' : ''}>
            <div className="flex items-start gap-4">
              <div className="flex flex-col items-center">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-600 text-sm font-bold text-white">
                  {idx + 1}
                </div>
                <span className="mt-1 text-[10px] text-slate-400">STEP</span>
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-base font-bold text-slate-900">{item.category}</span>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">
                    실무 랭킹 {item.rank}위
                  </span>
                </div>

                {/* 왜 먼저 봐야 하는지 근거 */}
                <p className="mt-1 text-xs text-indigo-600">
                  📌 선정 근거: 검색 {item.searchCount}회 · 조회 {item.viewCount}회 (실무자가 가장
                  자주 찾는 {item.rank}순위 주제)
                </p>
                <p className="mt-1 text-sm text-slate-500">
                  대표 질문: "{item.questionExample}"
                </p>

                <div className="mt-3 space-y-2">
                  <SectionLabel>먼저 볼 문서·조항</SectionLabel>
                  {item.relatedArticles.map((a, i) => (
                    <ArticleCard key={i} article={a} />
                  ))}
                </div>

                <button
                  onClick={() =>
                    setDone((prev) => ({ ...prev, [item.rank]: !prev[item.rank] }))
                  }
                  className={`mt-3 rounded-lg px-4 py-1.5 text-sm font-semibold ${
                    done[item.rank]
                      ? 'border border-slate-300 text-slate-500'
                      : 'bg-emerald-600 text-white hover:bg-emerald-500'
                  }`}
                >
                  {done[item.rank] ? '학습 완료됨 ✓' : '학습 완료로 표시'}
                </button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <p className="mt-5 rounded-lg bg-slate-100 p-3 text-xs text-slate-500">
        🔄 선순환: 신규입사자의 질의·조회도 DB에 누적되어 다음 랭킹에 반영됩니다. 기간이 바뀌면
        위 학습 순서도 자동으로 최신화됩니다.
      </p>
    </div>
  )
}
