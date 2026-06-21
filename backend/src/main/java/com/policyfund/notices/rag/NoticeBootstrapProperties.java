package com.policyfund.notices.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 최초 부팅 시 원본 공고를 각 카테고리의 v1 로 시드하기 위한 설정(notices.bootstrap.*). */
@Component
@ConfigurationProperties(prefix = "notices.bootstrap")
public class NoticeBootstrapProperties {

    /** 부팅 시 원본 공고를 v1 로 시드할지 여부(운영 컨테이너에서만 true 권장). */
    private boolean enabled = false;

    /** category(regulation/reference) → 원본 PDF 경로(작업 디렉터리 기준 상대경로 가능). */
    private Map<String, String> sources = new LinkedHashMap<>();

    /** 시드 v1 의 시행일(원본 공고 baseline). */
    private LocalDate effectiveDate = LocalDate.parse("2026-01-01");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
}
