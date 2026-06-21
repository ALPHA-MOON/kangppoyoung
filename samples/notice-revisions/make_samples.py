# -*- coding: utf-8 -*-
"""공고/참고자료 개정본 등록·diff·RAG 검색 테스트용 샘플 PDF 생성기.

각 문서는 3페이지로, 1·3페이지는 v1/v2 동일(diff '동일' 블록), 2페이지만 개정(diff 추가/삭제 블록).
개정본(v2) 2페이지에는 v1에 없던 distinctive 조항을 넣어 RAG 검색으로 '개정판 반영'을 확인할 수 있게 한다.

사용: python make_samples.py  → regulation_v1.pdf, regulation_v2.pdf, reference_v1.pdf, reference_v2.pdf
한글 렌더·텍스트 추출 안정성을 위해 Malgun Gothic(TTF) 임베드.
"""
import os
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

FONT = "Malgun"
pdfmetrics.registerFont(TTFont(FONT, r"C:\Windows\Fonts\malgun.ttf"))
OUT = os.path.dirname(os.path.abspath(__file__))


def wrap(line, n=33):
    # 공백(단어) 경계로만 줄바꿈 — 한글 복합어/고유명사가 줄 중간에서 잘리지 않게.
    words = line.split(" ")
    out, cur = [], ""
    for w in words:
        cand = w if not cur else cur + " " + w
        if cur and len(cand) > n:
            out.append(cur)
            cur = w
        else:
            cur = cand
    if cur:
        out.append(cur)
    return out


def make_pdf(path, pages):
    c = canvas.Canvas(path, pagesize=A4)
    w, h = A4
    for page in pages:
        c.setFont(FONT, 13)
        y = h - 72
        for raw in page:
            for line in wrap(raw):
                c.drawString(72, y, line)
                y -= 24
            y -= 4  # 문단 간격
        c.showPage()
    c.save()
    print("wrote", path)


# ---------- 공고(regulation) ----------
reg_p1 = [
    "정책자금 지원 공고 (테스트 샘플)",
    "제1조(목적) 이 공고는 중소기업 정책자금 융자 지원의 기준과 절차를 정함을 목적으로 한다.",
    "제2조(신청자격) 신청 자격은 중소기업기본법상 중소기업 및 소상공인으로 한다.",
]
reg_p2_v1 = [
    "제5조(지원한도) 기업당 정책자금 지원한도는 10억원으로 한다.",
    "제6조(금리) 융자 금리는 연 2.5퍼센트를 기준으로 한다.",
]
reg_p2_v2 = [
    "제5조(지원한도) 기업당 정책자금 지원한도는 30억원으로 한다.",
    "제5조의2(수출기업 우대한도) 직전연도 수출실적이 있는 수출기업은 최대 50억원까지 우대 지원한다.",
    "제6조(금리) 융자 금리는 연 2.5퍼센트를 기준으로 한다.",
]
reg_p3 = [
    "제8조(상환) 상환 기간은 거치기간 2년을 포함하여 최대 8년으로 한다.",
    "부칙 이 공고는 공고한 날부터 시행한다.",
]
make_pdf(os.path.join(OUT, "regulation_v1.pdf"), [reg_p1, reg_p2_v1, reg_p3])
make_pdf(os.path.join(OUT, "regulation_v2.pdf"), [reg_p1, reg_p2_v2, reg_p3])

# ---------- 참고자료(reference) ----------
ref_p1 = [
    "정책자금 참고자료 (테스트 샘플)",
    "[참고1] 혁신성장 분야 우대 안내",
    "혁신성장 분야를 영위하는 기업은 평가 시 별도 우대를 받을 수 있다.",
]
ref_p2_v1 = [
    "[참고2] 신청 서류 안내",
    "제출 서류는 사업자등록증, 재무제표, 사업계획서로 한다.",
]
ref_p2_v2 = [
    "[참고2] 신청 서류 안내",
    "제출 서류는 사업자등록증, 재무제표, 사업계획서, 수출실적증명서로 한다.",
    "[참고2의2] 온라인 제출 안내 모든 신청 서류는 정책자금 누리집을 통한 온라인 접수만 허용한다.",
]
ref_p3 = [
    "[참고3] 문의처 안내",
    "자세한 사항은 지역 중소벤처기업진흥공단 지역본부로 문의한다.",
]
make_pdf(os.path.join(OUT, "reference_v1.pdf"), [ref_p1, ref_p2_v1, ref_p3])
make_pdf(os.path.join(OUT, "reference_v2.pdf"), [ref_p1, ref_p2_v2, ref_p3])

print("done")
