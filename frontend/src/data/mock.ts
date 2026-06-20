// 정책자금 지원 업무 플랫폼 — 유저 플로우 검증용 목업 데이터
// 핵심 설계: UC-4(유사 질문 카테고리·랭킹)가 UC-5(신규입사자 온보딩)의
// 학습 우선순위 데이터 소스가 된다.

export type DocType = '규정' | '지침' | '절차'

export interface PolicyDocument {
  id: string
  title: string
  type: DocType
  updatedAt: string
  /** 단일 진실 문서 여부 (UC-3) */
  isSingleSource: boolean
}

export interface Article {
  docId: string
  docTitle: string
  docType: DocType
  articleNo: string // 예: 제5조 2항
  text: string
}

/** UC-1 검색 결과: 근거 + 중복(요약) / 상충(원문 병렬) */
export interface SearchResult {
  query: string
  answer: string
  /** 답변의 직접 근거 조항 */
  evidence: Article[]
  /** 중복 절차: 하나로 요약 + 출처 */
  duplicateSummary?: {
    summary: string
    sources: Article[]
  }
  /** 상충 절차: 원문 그대로 병렬 + 출처 */
  conflicts?: Article[]
}

export interface RankingItem {
  rank: number
  category: string
  questionExample: string
  /** 조회·검색 횟수 (실무자가 많이 보고 검색한 정도) */
  searchCount: number
  viewCount: number
  trend: 'up' | 'down' | 'same'
  /** 이 카테고리의 핵심 근거 문서/조항 */
  relatedArticles: Article[]
}

export const DOCUMENTS: PolicyDocument[] = [
  { id: 'D-001', title: '정책자금 융자 운용 규정', type: '규정', updatedAt: '2026-05-28', isSingleSource: true },
  { id: 'D-002', title: '신청 자격 심사 지침', type: '지침', updatedAt: '2026-05-30', isSingleSource: true },
  { id: 'D-003', title: '서류 제출 및 접수 절차', type: '절차', updatedAt: '2026-06-01', isSingleSource: true },
  { id: 'D-004', title: '대출 한도 산정 지침', type: '지침', updatedAt: '2026-04-12', isSingleSource: true },
  { id: 'D-005', title: '상환 및 연체 관리 절차', type: '절차', updatedAt: '2026-05-15', isSingleSource: true },
  { id: 'D-006', title: '구(舊) 서류 접수 안내 (통합 전)', type: '절차', updatedAt: '2025-11-02', isSingleSource: false },
]

const A = (
  docId: string,
  docTitle: string,
  docType: DocType,
  articleNo: string,
  text: string,
): Article => ({ docId, docTitle, docType, articleNo, text })

/** 미리 정의된 검색 시나리오 (자연어 질의 → 근거) */
export const SEARCH_SCENARIOS: SearchResult[] = [
  {
    query: '서류 제출 기한이 어떻게 되나요?',
    answer:
      '서류 제출 기한은 접수 마감일로부터 영업일 기준 5일 이내입니다. 동일 내용이 두 문서에 중복 기재되어 있어 하나로 요약했습니다.',
    evidence: [
      A('D-003', '서류 제출 및 접수 절차', '절차', '제4조 1항', '신청 서류는 접수 마감일로부터 영업일 기준 5일 이내에 제출하여야 한다.'),
    ],
    duplicateSummary: {
      summary: '서류 제출 기한 = 접수 마감일 기준 영업일 5일 이내 (두 문서 내용 동일).',
      sources: [
        A('D-003', '서류 제출 및 접수 절차', '절차', '제4조 1항', '신청 서류는 접수 마감일로부터 영업일 기준 5일 이내에 제출하여야 한다.'),
        A('D-001', '정책자금 융자 운용 규정', '규정', '제12조', '제출 서류는 접수 마감 후 5영업일 이내 제출을 원칙으로 한다.'),
      ],
    },
  },
  {
    query: '대출 한도는 매출액 기준인가요 자기자본 기준인가요?',
    answer:
      '두 지침이 서로 다른 기준을 제시하고 있어 상충됩니다. 임의 통합 없이 원문을 그대로 병렬 표시합니다. 적용 기준은 담당 부서 확인이 필요합니다.',
    evidence: [],
    conflicts: [
      A('D-004', '대출 한도 산정 지침', '지침', '제3조', '대출 한도는 직전 연도 매출액의 30% 이내로 산정한다.'),
      A('D-001', '정책자금 융자 운용 규정', '규정', '제8조 2항', '대출 한도는 자기자본의 200% 범위 내에서 산정한다.'),
    ],
  },
  {
    query: '연체 시 어떤 절차가 진행되나요?',
    answer:
      '연체 발생 시 1차 안내(7일) → 2차 독촉(15일) → 기한이익 상실 순으로 진행됩니다.',
    evidence: [
      A('D-005', '상환 및 연체 관리 절차', '절차', '제6조', '연체 발생일로부터 7일 이내 1차 안내, 15일 경과 시 2차 독촉을 시행한다.'),
      A('D-005', '상환 및 연체 관리 절차', '절차', '제7조', '2차 독촉 후에도 미상환 시 기한이익 상실 처리한다.'),
    ],
  },
]

/** UC-4: 유사 질문 카테고리·랭킹 (기간별) */
export const RANKING_BY_PERIOD: Record<string, RankingItem[]> = {
  '최근 7일': [
    {
      rank: 1,
      category: '서류 제출·접수',
      questionExample: '서류 제출 기한이 어떻게 되나요?',
      searchCount: 142,
      viewCount: 318,
      trend: 'up',
      relatedArticles: [
        A('D-003', '서류 제출 및 접수 절차', '절차', '제4조 1항', '신청 서류는 접수 마감일로부터 영업일 기준 5일 이내에 제출하여야 한다.'),
      ],
    },
    {
      rank: 2,
      category: '신청 자격',
      questionExample: '소상공인도 신청 가능한가요?',
      searchCount: 118,
      viewCount: 264,
      trend: 'same',
      relatedArticles: [
        A('D-002', '신청 자격 심사 지침', '지침', '제2조', '신청 자격은 중소기업기본법상 중소기업 및 소상공인으로 한다.'),
      ],
    },
    {
      rank: 3,
      category: '대출 한도',
      questionExample: '대출 한도는 어떻게 산정되나요?',
      searchCount: 97,
      viewCount: 201,
      trend: 'up',
      relatedArticles: [
        A('D-004', '대출 한도 산정 지침', '지침', '제3조', '대출 한도는 직전 연도 매출액의 30% 이내로 산정한다.'),
      ],
    },
    {
      rank: 4,
      category: '상환·연체',
      questionExample: '연체하면 어떻게 되나요?',
      searchCount: 64,
      viewCount: 130,
      trend: 'down',
      relatedArticles: [
        A('D-005', '상환 및 연체 관리 절차', '절차', '제6조', '연체 발생일로부터 7일 이내 1차 안내, 15일 경과 시 2차 독촉을 시행한다.'),
      ],
    },
    {
      rank: 5,
      category: '금리·우대',
      questionExample: '우대 금리 조건이 있나요?',
      searchCount: 41,
      viewCount: 88,
      trend: 'same',
      relatedArticles: [
        A('D-001', '정책자금 융자 운용 규정', '규정', '제10조', '우대 금리는 고용 창출 실적에 따라 차등 적용한다.'),
      ],
    },
  ],
  '최근 30일': [
    {
      rank: 1,
      category: '신청 자격',
      questionExample: '소상공인도 신청 가능한가요?',
      searchCount: 520,
      viewCount: 1140,
      trend: 'up',
      relatedArticles: [
        A('D-002', '신청 자격 심사 지침', '지침', '제2조', '신청 자격은 중소기업기본법상 중소기업 및 소상공인으로 한다.'),
      ],
    },
    {
      rank: 2,
      category: '서류 제출·접수',
      questionExample: '서류 제출 기한이 어떻게 되나요?',
      searchCount: 488,
      viewCount: 1020,
      trend: 'same',
      relatedArticles: [
        A('D-003', '서류 제출 및 접수 절차', '절차', '제4조 1항', '신청 서류는 접수 마감일로부터 영업일 기준 5일 이내에 제출하여야 한다.'),
      ],
    },
    {
      rank: 3,
      category: '대출 한도',
      questionExample: '대출 한도는 어떻게 산정되나요?',
      searchCount: 351,
      viewCount: 760,
      trend: 'up',
      relatedArticles: [
        A('D-004', '대출 한도 산정 지침', '지침', '제3조', '대출 한도는 직전 연도 매출액의 30% 이내로 산정한다.'),
      ],
    },
    {
      rank: 4,
      category: '상환·연체',
      questionExample: '연체하면 어떻게 되나요?',
      searchCount: 240,
      viewCount: 503,
      trend: 'same',
      relatedArticles: [
        A('D-005', '상환 및 연체 관리 절차', '절차', '제6조', '연체 발생일로부터 7일 이내 1차 안내, 15일 경과 시 2차 독촉을 시행한다.'),
      ],
    },
    {
      rank: 5,
      category: '금리·우대',
      questionExample: '우대 금리 조건이 있나요?',
      searchCount: 162,
      viewCount: 333,
      trend: 'down',
      relatedArticles: [
        A('D-001', '정책자금 융자 운용 규정', '규정', '제10조', '우대 금리는 고용 창출 실적에 따라 차등 적용한다.'),
      ],
    },
  ],
}

export const RANKING_PERIODS = Object.keys(RANKING_BY_PERIOD)
