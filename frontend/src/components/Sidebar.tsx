import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

const linkClass = (isActive: boolean) =>
  `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition ${
    isActive ? 'bg-slate-900 font-semibold text-white' : 'text-slate-600 hover:bg-slate-100'
  }`

export default function Sidebar() {
  const location = useLocation()
  const noticeOpen = location.pathname.startsWith('/notice')
  const [open, setOpen] = useState(noticeOpen)

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-5 py-5">
        <div className="text-base font-bold leading-tight text-slate-900">정책자금 지원</div>
        <div className="text-sm text-slate-500">업무 플랫폼</div>
      </div>

      <nav className="flex-1 space-y-1 p-3">
        <NavLink to="/" end className={({ isActive }) => linkClass(isActive)}>
          <span className="text-base">🔍</span>
          <span className="flex-1">통합 검색</span>
        </NavLink>

        {/* 정책 자금 공고 — 소메뉴 그룹 */}
        <div>
          <button
            onClick={() => setOpen((v) => !v)}
            className={linkClass(noticeOpen) + ' w-full'}
          >
            <span className="text-base">📋</span>
            <span className="flex-1 text-left">정책 자금 공고</span>
            <span className={`text-xs transition-transform ${open ? 'rotate-90' : ''}`}>›</span>
          </button>
          {open && (
            <div className="mt-1 space-y-1 pl-7">
              <NavLink
                to="/notice/regulation"
                className={({ isActive }) =>
                  `block rounded-lg px-3 py-2 text-sm transition ${
                    isActive ? 'bg-slate-100 font-semibold text-slate-900' : 'text-slate-500 hover:bg-slate-100'
                  }`
                }
              >
                공고
              </NavLink>
              <NavLink
                to="/notice/reference"
                className={({ isActive }) =>
                  `block rounded-lg px-3 py-2 text-sm transition ${
                    isActive ? 'bg-slate-100 font-semibold text-slate-900' : 'text-slate-500 hover:bg-slate-100'
                  }`
                }
              >
                참고자료
              </NavLink>
            </div>
          )}
        </div>

        <NavLink to="/ranking" className={({ isActive }) => linkClass(isActive)}>
          <span className="text-base">📊</span>
          <span className="flex-1">질문 분석</span>
        </NavLink>

        <NavLink to="/onboarding" className={({ isActive }) => linkClass(isActive)}>
          <span className="text-base">🎓</span>
          <span className="flex-1">온보딩 가이드</span>
        </NavLink>
      </nav>

      <div className="border-t border-slate-100 p-4 text-xs text-slate-400">
        © 2026 정책자금 지원팀
      </div>
    </aside>
  )
}
