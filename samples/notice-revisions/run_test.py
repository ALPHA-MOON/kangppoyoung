# -*- coding: utf-8 -*-
"""개정본 등록 → diff → RAG 검색 반영 자동 검증.

backend 를 직접 호출(기본 http://localhost:8080)해 두 카테고리의 v1/v2 PDF 를 모두 등록한 뒤,
diff 가 개정 내용을 보여주는지 + 검색이 개정판(v2) 내용을 답하는지 확인한다.
(두 카테고리를 먼저 모두 등록·재색인한 뒤 검색해, 한 카테고리가 다른 카테고리의 미교체 원본과 경쟁하는
오프라인 hash 검색의 순서 의존성을 제거한다.)
사용: python run_test.py [BASE_URL]
"""
import os, sys, time, requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080") + "/api/v1"
SAMP = os.path.dirname(os.path.abspath(__file__))
results = []


def preprocess(cat, fname):
    with open(os.path.join(SAMP, fname), "rb") as f:
        r = requests.post(f"{BASE}/notices/{cat}/revisions/preprocess",
                          files={"file": (fname, f, "application/pdf")}, timeout=180)
    r.raise_for_status()
    return r.json()


def register(cat, date, pp):
    r = requests.post(f"{BASE}/notices/{cat}/revisions",
                      json={"effectiveDate": date, "blocks": pp["blocks"], "sourceRef": pp["sourceRef"]},
                      timeout=60)
    r.raise_for_status()
    return r.json()


def get_notice(cat):
    return requests.get(f"{BASE}/notices/{cat}", timeout=30).json()


def get_diff(cat, version):
    return requests.get(f"{BASE}/notices/{cat}/versions/{version}/diff", timeout=30).json()


def search_text(query):
    r = requests.post(f"{BASE}/search", json={"query": query}, timeout=60)
    r.raise_for_status()
    res = r.json()
    parts = [res.get("answer", "") or ""]
    for a in (res.get("evidence") or []):
        parts.append(a.get("text", "") or "")
    ds = res.get("duplicateSummary")
    if ds:
        parts.append(ds.get("summary", "") or "")
        for a in (ds.get("sources") or []):
            parts.append(a.get("text", "") or "")
    for a in (res.get("conflicts") or []):
        parts.append(a.get("text", "") or "")
    return "\n".join(parts)


def wait_search(query, needle, tries=120, delay=3):
    last = ""
    for _ in range(tries):
        try:
            last = search_text(query)
            if needle in last:
                return True, last
        except Exception as e:
            last = f"(err {e})"
        time.sleep(delay)
    return False, last


def check(name, cond):
    results.append((name, cond))
    print(("  PASS " if cond else "  FAIL ") + name)


def register_pair(cat, v1, v2):
    a = register(cat, "2026-02-01", preprocess(cat, v1))
    b = register(cat, "2026-03-01", preprocess(cat, v2))
    print(f"  {cat}: 등록 v1→{a['version']}, v2(개정)→{b['version']}")
    return b["version"]


def verify_diff(cat, latest_ver, add_needles, del_needle):
    diff = get_diff(cat, latest_ver)
    add_txt = "\n".join(d["block"].get("text", "") for d in diff if d["type"] == "add")
    del_txt = "\n".join(d["block"].get("text", "") for d in diff if d["type"] == "del")
    n_same = sum(1 for d in diff if d["type"] == "same")
    print(f"  {cat} diff: same={n_same} add={sum(1 for d in diff if d['type']=='add')} del={sum(1 for d in diff if d['type']=='del')}")
    for nd in add_needles:
        check(f"{cat}: diff 추가에 '{nd}'", nd in add_txt)
    check(f"{cat}: diff 삭제에 '{del_needle}'(원본 값)", del_needle in del_txt)
    check(f"{cat}: 동일 블록 존재(1·3페이지 유지)", n_same >= 1)


def verify_search(cat, q, needle):
    ok, txt = wait_search(q, needle)
    check(f"{cat}: 검색 '{q}' → 개정판 '{needle}' 반영", ok)
    if ok:
        snip = next((ln for ln in txt.split("\n") if needle in ln), "")[:90]
        print(f"      근거: …{snip}…")


print("===== 1) 두 카테고리 개정본 등록(v1 → v2) =====")
reg_latest = register_pair("regulation", "regulation_v1.pdf", "regulation_v2.pdf")
ref_latest = register_pair("reference", "reference_v1.pdf", "reference_v2.pdf")
check("regulation: 최신본이 방금 등록한 개정본", get_notice("regulation")["versions"][0]["version"] == reg_latest)
check("reference: 최신본이 방금 등록한 개정본", get_notice("reference")["versions"][0]["version"] == ref_latest)

print("\n===== 2) diff 검증(2페이지 개정, 1·3페이지 동일) =====")
verify_diff("regulation", reg_latest, ["30억", "수출기업", "50억"], "10억")
verify_diff("reference", ref_latest, ["수출실적증명서", "온라인"], "사업계획서")

print("\n===== 3) 검색: 개정판 내용이 RAG 로 반영되는지(비동기 재색인 완료까지 대기) =====")
verify_search("regulation", "수출기업 우대한도", "50억")
verify_search("regulation", "정책자금 지원한도는 얼마인가요?", "30억")
verify_search("reference", "수출실적증명서로 한다", "수출실적증명서")
verify_search("reference", "신청 서류는 온라인으로만 접수하나요?", "온라인")

print("\n===== 요약 =====")
passed = sum(1 for _, c in results if c)
print(f"{passed}/{len(results)} PASS")
sys.exit(0 if passed == len(results) else 1)
