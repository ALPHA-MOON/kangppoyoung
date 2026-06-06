import { useState } from 'react'
import { RANKING_BY_PERIOD, RANKING_PERIODS, type RankingItem } from '../data/mock'
import { Card, PageHeader, SectionLabel, SourceChip } from '../components/ui'

const trendIcon: Record<RankingItem['trend'], string> = {
  up: '🔺',
  down: '🔻',
  same: '➖',
}

export default function Ranking() {
  const [period, setPeriod] = useState(RANKING_PERIODS[0])
  const items = RANKING_BY_PERIOD[period]
  const max = Math.max(...items.map((i) => i.searchCount))

  return (
    <div>
      <PageHeader
        title="질문 분석"
        desc="저장된 질의·답변을 기간별로 카테고리화하고 빈도순 랭킹을 보여줍니다. 이 랭킹이 온보딩 가이드의 학습 우선순위로 활용됩니다."
      />

      <div className="mb-5 flex items-center gap-2">
        <SectionLabel>집계 기간</SectionLabel>
        <div className="flex gap-1">
          {RANKING_PERIODS.map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                period === p ? 'bg-slate-900 text-white' : 'border border-slate-200 text-slate-600'
              }`}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-3">
        {items.map((item) => (
          <Card key={item.rank}>
            <div className="flex items-start gap-4">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-900 text-sm font-bold text-white">
                {item.rank}
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-base font-bold text-slate-900">{item.category}</span>
                  <span className="text-xs">{trendIcon[item.trend]}</span>
                </div>
                <p className="mt-0.5 text-sm text-slate-500">대표 질문: "{item.questionExample}"</p>

                {/* 검색량 막대 */}
                <div className="mt-2 flex items-center gap-2">
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-slate-800"
                      style={{ width: `${(item.searchCount / max) * 100}%` }}
                    />
                  </div>
                  <span className="w-28 text-right text-xs text-slate-500">
                    검색 {item.searchCount} · 조회 {item.viewCount}
                  </span>
                </div>

                <div className="mt-2 flex flex-wrap gap-2">
                  {item.relatedArticles.map((a, i) => (
                    <SourceChip key={i} article={a} />
                  ))}
                </div>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <p className="mt-5 rounded-lg bg-indigo-50 p-3 text-xs text-indigo-700">
        💡 위 랭킹(많이 보고·많이 검색한 순)이 그대로 신규입사자 학습 우선순위로 환산됩니다. →
        온보딩 가이드에서 확인하세요.
      </p>
    </div>
  )
}
