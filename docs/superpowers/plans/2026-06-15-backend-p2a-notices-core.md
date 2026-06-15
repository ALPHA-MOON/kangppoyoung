# Backend P2a — Notices Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 정책 자금 공고 문서/버전 조회·개정본 등록·버전 diff API를 구현한다(OpenAI 불필요). PDF Vision 전처리는 P2b 에서 별도 구현한다.

**Architecture:** Controller→Service→Repository. `ContentBlock`(text/image) 폴리모픽 DTO 를 Jackson 으로 직렬화하고 MySQL `JSON` 컬럼(Hibernate `@JdbcTypeCode(SqlTypes.JSON)`)에 저장. 버전은 날짜 내림차순 정렬, 개정 등록은 새 버전 누적, diff 는 LCS 블록 비교(서버 계산).

**Tech Stack:** Java 25, Spring Boot 3.4.5, Spring Data JPA, MySQL 8(Testcontainers), Flyway, JUnit 5, MockMvc. (Gradle 9.5.1 wrapper.)

**관련 문서:** [BACKEND_PRD](../../prd/BACKEND_PRD.md) §5.2 · [backend_user_flow](../../user_flow/backend_user_flow.md) UC-3 · [OpenAPI](../../api/openapi.yaml)

**전제(P1 완료 상태):** Spring Boot 골격, Flyway `V1__init_schema.sql`(notice_category/notice_version 포함), 전역 에러 처리(`ResourceNotFoundException`→404 등), `AbstractIntegrationTest`(싱글톤 Testcontainers MySQL) 가 이미 있다.

**제약(반드시 준수):**
- 신규 코드는 `backend/` 아래에만. **기존 파일(`docs/`,`frontend/`,`openapi.yaml`)은 수정 금지.**
- **빌드/버전 변경 금지:** Java 25 toolchain, Gradle 9.5.1, `build.gradle` 의존성은 이 플랜이 지시할 때만 추가. foojay/머신 Docker 설정을 레포에 넣지 않는다.
- 커밋은 각 Task 끝에서 **스코프된 `git add`**(나열된 경로만, `git add -A` 금지). 메시지 영어, 본문 마지막 줄:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- 통합 테스트는 `AbstractIntegrationTest` 를 상속만 하면 Docker 연결이 동작한다(머신 설정은 `~/.gradle/init.gradle`·`~/.testcontainers.properties` 에 이미 있음).
- 패키지 루트 `kr.co.hakjisa.policyfund`. notices 모듈은 `kr.co.hakjisa.policyfund.notices` 하위.
- 컨트롤러 경로는 `/api/v1/...` (프론트 `frontend/src/api` 와 OpenAPI 계약 일치).
- Windows; `./gradlew` 는 `backend/` 에서 실행(현재 셸 cwd 가 backend 면 `cd` 생략).

---

## File Structure (P2a 신규 생성)

```
backend/src/main/java/kr/co/hakjisa/policyfund/notices/
  dto/ContentBlock.java            # sealed: TextBlock | ImageBlock (Jackson 폴리모픽)
  dto/NoticeVersionDto.java        # {version, date, blocks[]}
  dto/NoticeCategoryDto.java       # {key, label, docTitle, versions[]}
  dto/NoticeRevisionRequest.java   # {effectiveDate, blocks[]}
  dto/DiffBlock.java               # {type: same|add|del, block}
  domain/NoticeCategoryEntity.java
  domain/NoticeVersionEntity.java
  domain/NoticeCategoryRepository.java
  domain/NoticeVersionRepository.java
  service/BlockDiff.java           # LCS 블록 diff 유틸
  service/NoticeService.java
  web/NoticeController.java
backend/src/main/resources/db/migration/
  V2__seed_notice_categories.sql   # regulation / reference 시드
backend/src/test/java/kr/co/hakjisa/policyfund/notices/
  dto/ContentBlockJsonTest.java
  service/BlockDiffTest.java
  NoticeRepositoryIntegrationTest.java
  NoticeApiIntegrationTest.java
```

---

## Task 1: ContentBlock 폴리모픽 DTO + JSON 직렬화

**Files:**
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/notices/dto/ContentBlock.java`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/notices/dto/ContentBlockJsonTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`ContentBlockJsonTest.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textBlock_serializesWithTypeDiscriminator() throws Exception {
        String json = mapper.writeValueAsString(new ContentBlock.TextBlock("제5조 내용"));
        assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"제5조 내용\"");
    }

    @Test
    void imageBlock_roundTrips() throws Exception {
        ContentBlock original = new ContentBlock.ImageBlock("/api/v1/notices/assets/abc", "표1.png");
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"type\":\"image\"");
        ContentBlock back = mapper.readValue(json, ContentBlock.class);
        assertThat(back).isEqualTo(original);
    }

    @Test
    void list_deserializesMixedBlocks() throws Exception {
        String json = "[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"image\",\"src\":\"s\",\"name\":\"n\"}]";
        List<ContentBlock> blocks = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, ContentBlock.class));
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(blocks.get(1)).isInstanceOf(ContentBlock.ImageBlock.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*ContentBlockJsonTest"`
Expected: FAIL (ContentBlock 미정의).

- [ ] **Step 3: 구현**

`ContentBlock.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 본문 블록(OpenAPI ContentBlock). type 판별자로 text/image 를 구분한다.
 * 직렬화 시 {"type":"text",...} / {"type":"image",...} 형태가 되어 계약과 일치한다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image")
})
public sealed interface ContentBlock permits ContentBlock.TextBlock, ContentBlock.ImageBlock {

    record TextBlock(String text) implements ContentBlock {}

    record ImageBlock(String src, String name) implements ContentBlock {}
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*ContentBlockJsonTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/notices/dto/ContentBlock.java backend/src/test/java/kr/co/hakjisa/policyfund/notices/dto/ContentBlockJsonTest.java
git commit -m "$(cat <<'EOF'
feat(backend): add polymorphic ContentBlock DTO (text|image)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 엔티티 + 리포지토리 + 카테고리 시드(V2)

**Files:**
- Create: `domain/NoticeCategoryEntity.java`, `domain/NoticeVersionEntity.java`, `domain/NoticeCategoryRepository.java`, `domain/NoticeVersionRepository.java` (패키지 `kr.co.hakjisa.policyfund.notices.domain`)
- Create: `backend/src/main/resources/db/migration/V2__seed_notice_categories.sql`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/notices/NoticeRepositoryIntegrationTest.java`

- [ ] **Step 1: 실패 통합 테스트 작성**

`NoticeRepositoryIntegrationTest.java`:
```java
package kr.co.hakjisa.policyfund.notices;

import kr.co.hakjisa.policyfund.notices.domain.NoticeVersionEntity;
import kr.co.hakjisa.policyfund.notices.domain.NoticeVersionRepository;
import kr.co.hakjisa.policyfund.notices.dto.ContentBlock;
import kr.co.hakjisa.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    NoticeVersionRepository versions;

    @Test
    void savesBlocksAsJsonAndQueriesByDateDesc() {
        versions.save(new NoticeVersionEntity("regulation", "v1", LocalDate.parse("2026-01-01"),
                List.of(new ContentBlock.TextBlock("v1 본문"))));
        versions.save(new NoticeVersionEntity("regulation", "v2", LocalDate.parse("2026-02-01"),
                List.of(new ContentBlock.TextBlock("v2 본문"),
                        new ContentBlock.ImageBlock("/api/v1/notices/assets/x", "그림.png"))));

        List<NoticeVersionEntity> result =
                versions.findByCategoryKeyOrderByDateDescVersionDesc("regulation");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersion()).isEqualTo("v2");          // 최신 먼저
        assertThat(result.get(0).getBlocks()).hasSize(2);
        assertThat(result.get(0).getBlocks().get(1)).isInstanceOf(ContentBlock.ImageBlock.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*NoticeRepositoryIntegrationTest"`
Expected: FAIL (엔티티/리포지토리 미정의).

- [ ] **Step 3: 엔티티/리포지토리/시드 구현**

`NoticeCategoryEntity.java`:
```java
package kr.co.hakjisa.policyfund.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notice_category")
public class NoticeCategoryEntity {

    @Id
    @Column(name = "`key`")
    private String key;

    private String label;

    @Column(name = "doc_title")
    private String docTitle;

    protected NoticeCategoryEntity() {}

    public NoticeCategoryEntity(String key, String label, String docTitle) {
        this.key = key;
        this.label = label;
        this.docTitle = docTitle;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getDocTitle() { return docTitle; }
}
```

`NoticeVersionEntity.java`:
```java
package kr.co.hakjisa.policyfund.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.co.hakjisa.policyfund.notices.dto.ContentBlock;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "notice_version")
public class NoticeVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_key")
    private String categoryKey;

    private String version;

    @Column(name = "`date`")
    private LocalDate date;

    // MySQL JSON 컬럼에 ContentBlock[] 저장 (Hibernate 6 네이티브 JSON, Jackson 폴리모픽 보존).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocks_json", columnDefinition = "json")
    private List<ContentBlock> blocks;

    protected NoticeVersionEntity() {}

    public NoticeVersionEntity(String categoryKey, String version, LocalDate date, List<ContentBlock> blocks) {
        this.categoryKey = categoryKey;
        this.version = version;
        this.date = date;
        this.blocks = blocks;
    }

    public Long getId() { return id; }
    public String getCategoryKey() { return categoryKey; }
    public String getVersion() { return version; }
    public LocalDate getDate() { return date; }
    public List<ContentBlock> getBlocks() { return blocks; }
}
```

`NoticeCategoryRepository.java`:
```java
package kr.co.hakjisa.policyfund.notices.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeCategoryRepository extends JpaRepository<NoticeCategoryEntity, String> {
}
```

`NoticeVersionRepository.java`:
```java
package kr.co.hakjisa.policyfund.notices.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoticeVersionRepository extends JpaRepository<NoticeVersionEntity, Long> {

    List<NoticeVersionEntity> findByCategoryKeyOrderByDateDescVersionDesc(String categoryKey);

    Optional<NoticeVersionEntity> findByCategoryKeyAndVersion(String categoryKey, String version);
}
```

`V2__seed_notice_categories.sql`:
```sql
INSERT INTO notice_category (`key`, label, doc_title) VALUES
  ('regulation', '공고',     '정책자금 지원 공고'),
  ('reference',  '참고자료', '정책자금 참고자료');
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*NoticeRepositoryIntegrationTest"`
Expected: PASS. (만약 `ddl-auto: validate` 가 JSON 컬럼에 실패하면 `@JdbcTypeCode(SqlTypes.JSON)` 가 정확히 적용됐는지 확인 — Hibernate 6 은 MySQL json 컬럼을 SqlTypes.JSON 으로 검증 통과한다.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/notices/domain backend/src/main/resources/db/migration/V2__seed_notice_categories.sql backend/src/test/java/kr/co/hakjisa/policyfund/notices/NoticeRepositoryIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(backend): add notice entities, repositories and category seed

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `GET /notices/{category}` (DTO·서비스·컨트롤러)

**Files:**
- Create: `dto/NoticeVersionDto.java`, `dto/NoticeCategoryDto.java`, `service/NoticeService.java`, `web/NoticeController.java`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/notices/NoticeApiIntegrationTest.java`

- [ ] **Step 1: 실패 통합 테스트 작성**

`NoticeApiIntegrationTest.java`:
```java
package kr.co.hakjisa.policyfund.notices;

import kr.co.hakjisa.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class NoticeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getNotice_returnsSeededCategory() throws Exception {
        mvc.perform(get("/api/v1/notices/regulation"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.key").value("regulation"))
           .andExpect(jsonPath("$.label").value("공고"))
           .andExpect(jsonPath("$.versions").isArray());
    }

    @Test
    void getNotice_unknownCategory_returns404() throws Exception {
        mvc.perform(get("/api/v1/notices/unknown"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").exists());
    }
}
```

> 주의: `AbstractIntegrationTest` 는 `webEnvironment = RANDOM_PORT` 라 `@AutoConfigureMockMvc` 를 함께 붙여 MockMvc 를 활성화한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*NoticeApiIntegrationTest"`
Expected: FAIL (컨트롤러/서비스 미정의).

- [ ] **Step 3: 구현**

`NoticeVersionDto.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

import java.time.LocalDate;
import java.util.List;

public record NoticeVersionDto(String version, LocalDate date, List<ContentBlock> blocks) {}
```

`NoticeCategoryDto.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

import java.util.List;

public record NoticeCategoryDto(String key, String label, String docTitle, List<NoticeVersionDto> versions) {}
```

`NoticeService.java`:
```java
package kr.co.hakjisa.policyfund.notices.service;

import kr.co.hakjisa.policyfund.common.error.ResourceNotFoundException;
import kr.co.hakjisa.policyfund.notices.domain.NoticeCategoryEntity;
import kr.co.hakjisa.policyfund.notices.domain.NoticeCategoryRepository;
import kr.co.hakjisa.policyfund.notices.domain.NoticeVersionEntity;
import kr.co.hakjisa.policyfund.notices.domain.NoticeVersionRepository;
import kr.co.hakjisa.policyfund.notices.dto.NoticeCategoryDto;
import kr.co.hakjisa.policyfund.notices.dto.NoticeVersionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoticeService {

    private final NoticeCategoryRepository categories;
    private final NoticeVersionRepository versions;

    public NoticeService(NoticeCategoryRepository categories, NoticeVersionRepository versions) {
        this.categories = categories;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public NoticeCategoryDto getNotice(String category) {
        NoticeCategoryEntity cat = categories.findById(category)
                .orElseThrow(() -> new ResourceNotFoundException("NOTICE_CATEGORY_NOT_FOUND",
                        "공고 카테고리를 찾을 수 없습니다: " + category));

        List<NoticeVersionDto> versionDtos =
                versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                        .map(this::toDto)
                        .toList();

        return new NoticeCategoryDto(cat.getKey(), cat.getLabel(), cat.getDocTitle(), versionDtos);
    }

    private NoticeVersionDto toDto(NoticeVersionEntity e) {
        return new NoticeVersionDto(e.getVersion(), e.getDate(), e.getBlocks());
    }
}
```

`NoticeController.java`:
```java
package kr.co.hakjisa.policyfund.notices.web;

import kr.co.hakjisa.policyfund.notices.dto.NoticeCategoryDto;
import kr.co.hakjisa.policyfund.notices.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping("/{category}")
    public NoticeCategoryDto getNotice(@PathVariable String category) {
        return service.getNotice(category);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*NoticeApiIntegrationTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/notices/dto/NoticeVersionDto.java backend/src/main/java/kr/co/hakjisa/policyfund/notices/dto/NoticeCategoryDto.java backend/src/main/java/kr/co/hakjisa/policyfund/notices/service/NoticeService.java backend/src/main/java/kr/co/hakjisa/policyfund/notices/web/NoticeController.java backend/src/test/java/kr/co/hakjisa/policyfund/notices/NoticeApiIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(backend): add GET /notices/{category}

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `POST /notices/{category}/revisions` (개정본 등록)

**Files:**
- Create: `dto/NoticeRevisionRequest.java`
- Modify: `service/NoticeService.java` (registerRevision 추가), `web/NoticeController.java` (POST 추가)
- Modify(test): `NoticeApiIntegrationTest.java` (등록 테스트 추가)

- [ ] **Step 1: 실패 테스트 추가**

`NoticeApiIntegrationTest.java` 상단 import 에 추가:
```java
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```
클래스 안에 메서드 추가:
```java
    @Test
    void registerRevision_thenAppearsAsLatest() throws Exception {
        String body = """
            {"effectiveDate":"2026-03-01",
             "blocks":[{"type":"text","text":"개정 본문"}]}
            """;

        mvc.perform(post("/api/v1/notices/reference/revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.version").value("v1"))
           .andExpect(jsonPath("$.date").value("2026-03-01"))
           .andExpect(jsonPath("$.blocks[0].type").value("text"));

        String body2 = """
            {"effectiveDate":"2026-04-01",
             "blocks":[{"type":"text","text":"두번째 개정"}]}
            """;
        mvc.perform(post("/api/v1/notices/reference/revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body2))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.version").value("v2"));

        mvc.perform(get("/api/v1/notices/reference"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.versions[0].version").value("v2"));
    }

    @Test
    void registerRevision_missingEffectiveDate_returns400() throws Exception {
        mvc.perform(post("/api/v1/notices/reference/revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blocks\":[{\"type\":\"text\",\"text\":\"x\"}]}"))
           .andExpect(status().isBadRequest());
    }
```

> 주의: 통합 테스트는 싱글톤 컨테이너를 공유하므로 데이터가 누적될 수 있다. 등록 테스트는 `reference` 카테고리에서 v1→v2 를 자체 생성·검증하고, diff 테스트(Task 5)는 `regulation` 을 쓰므로 서로 충돌하지 않는다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*NoticeApiIntegrationTest"`
Expected: FAIL (POST 매핑 없음 → 405/404).

- [ ] **Step 3: 구현**

`NoticeRevisionRequest.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record NoticeRevisionRequest(
        @NotNull LocalDate effectiveDate,
        @NotEmpty List<ContentBlock> blocks) {}
```

`NoticeService.java` 상단 import 에 추가:
```java
import kr.co.hakjisa.policyfund.notices.dto.NoticeRevisionRequest;
```
클래스 안에 메서드 추가:
```java
    @Transactional
    public NoticeVersionDto registerRevision(String category, NoticeRevisionRequest request) {
        categories.findById(category)
                .orElseThrow(() -> new ResourceNotFoundException("NOTICE_CATEGORY_NOT_FOUND",
                        "공고 카테고리를 찾을 수 없습니다: " + category));

        int next = versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                .map(NoticeVersionEntity::getVersion)
                .map(NoticeService::parseVersionNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        NoticeVersionEntity saved = versions.save(new NoticeVersionEntity(
                category, "v" + next, request.effectiveDate(), request.blocks()));

        return toDto(saved);
    }

    private static int parseVersionNumber(String version) {
        try {
            return version.startsWith("v") ? Integer.parseInt(version.substring(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
```

`NoticeController.java` 상단 import 에 추가:
```java
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import kr.co.hakjisa.policyfund.notices.dto.NoticeVersionDto;
import kr.co.hakjisa.policyfund.notices.dto.NoticeRevisionRequest;
```
클래스 안에 메서드 추가:
```java
    @PostMapping("/{category}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeVersionDto registerRevision(@PathVariable String category,
                                             @Valid @RequestBody NoticeRevisionRequest request) {
        return service.registerRevision(category, request);
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*NoticeApiIntegrationTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/notices backend/src/test/java/kr/co/hakjisa/policyfund/notices/NoticeApiIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(backend): add POST /notices/{category}/revisions

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `GET /notices/{category}/versions/{version}/diff` (LCS 블록 diff)

**Files:**
- Create: `dto/DiffBlock.java`, `service/BlockDiff.java`
- Modify: `service/NoticeService.java` (diff 추가), `web/NoticeController.java` (GET diff 추가)
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/notices/service/BlockDiffTest.java`, `NoticeApiIntegrationTest.java`(diff 테스트 추가)

- [ ] **Step 1: 실패 단위 테스트 작성 (LCS 알고리즘)**

`BlockDiffTest.java`:
```java
package kr.co.hakjisa.policyfund.notices.service;

import kr.co.hakjisa.policyfund.notices.dto.ContentBlock;
import kr.co.hakjisa.policyfund.notices.dto.DiffBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDiffTest {

    @Test
    void detectsSameAddDel() {
        List<ContentBlock> oldB = List.of(
                new ContentBlock.TextBlock("A"),
                new ContentBlock.TextBlock("B"));
        List<ContentBlock> newB = List.of(
                new ContentBlock.TextBlock("A"),
                new ContentBlock.TextBlock("C"));

        List<DiffBlock> diff = BlockDiff.diff(oldB, newB);

        assertThat(diff).extracting(DiffBlock::type)
                .containsExactly("same", "del", "add");
        assertThat(((ContentBlock.TextBlock) diff.get(0).block()).text()).isEqualTo("A");
        assertThat(((ContentBlock.TextBlock) diff.get(1).block()).text()).isEqualTo("B");
        assertThat(((ContentBlock.TextBlock) diff.get(2).block()).text()).isEqualTo("C");
    }

    @Test
    void noPrevious_allAdded() {
        List<DiffBlock> diff = BlockDiff.diff(List.of(),
                List.of(new ContentBlock.TextBlock("X")));
        assertThat(diff).extracting(DiffBlock::type).containsExactly("add");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*BlockDiffTest"`
Expected: FAIL (BlockDiff/DiffBlock 미정의).

- [ ] **Step 3: 구현**

`DiffBlock.java`:
```java
package kr.co.hakjisa.policyfund.notices.dto;

/** 버전 간 변경 단위. type: same(동일) / add(추가, 초록) / del(삭제, 빨강). */
public record DiffBlock(String type, ContentBlock block) {}
```

`BlockDiff.java`:
```java
package kr.co.hakjisa.policyfund.notices.service;

import kr.co.hakjisa.policyfund.notices.dto.ContentBlock;
import kr.co.hakjisa.policyfund.notices.dto.DiffBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 블록 단위 LCS diff. 동등성은 ContentBlock(record)의 equals 로 판단한다:
 * TextBlock 은 text, ImageBlock 은 src+name 기준.
 * (이미지 콘텐츠 해시 동등성[발견 #11]은 자산 저장이 생기는 P2b 에서 src 를 콘텐츠 주소화하여 충족.)
 */
public final class BlockDiff {

    private BlockDiff() {}

    public static List<DiffBlock> diff(List<ContentBlock> oldBlocks, List<ContentBlock> newBlocks) {
        int n = oldBlocks.size();
        int m = newBlocks.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = oldBlocks.get(i).equals(newBlocks.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<DiffBlock> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (oldBlocks.get(i).equals(newBlocks.get(j))) {
                out.add(new DiffBlock("same", newBlocks.get(j)));
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffBlock("del", oldBlocks.get(i)));
                i++;
            } else {
                out.add(new DiffBlock("add", newBlocks.get(j)));
                j++;
            }
        }
        while (i < n) out.add(new DiffBlock("del", oldBlocks.get(i++)));
        while (j < m) out.add(new DiffBlock("add", newBlocks.get(j++)));
        return out;
    }
}
```

- [ ] **Step 4: 단위 테스트 통과 확인**

Run: `./gradlew test --tests "*BlockDiffTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 서비스/컨트롤러에 diff 엔드포인트 추가**

`NoticeService.java` 상단 import 에 추가:
```java
import kr.co.hakjisa.policyfund.notices.dto.DiffBlock;
import kr.co.hakjisa.policyfund.notices.dto.ContentBlock;
```
클래스 안에 메서드 추가:
```java
    @Transactional(readOnly = true)
    public List<DiffBlock> diff(String category, String version) {
        // 카테고리 버전들을 날짜 오름차순(같으면 버전 오름차순)으로 정렬.
        List<NoticeVersionEntity> ordered =
                versions.findByCategoryKeyOrderByDateDescVersionDesc(category).stream()
                        .sorted(java.util.Comparator
                                .comparing(NoticeVersionEntity::getDate)
                                .thenComparing(e -> parseVersionNumber(e.getVersion())))
                        .toList();

        int idx = -1;
        for (int k = 0; k < ordered.size(); k++) {
            if (ordered.get(k).getVersion().equals(version)) { idx = k; break; }
        }
        if (idx < 0) {
            throw new ResourceNotFoundException("NOTICE_VERSION_NOT_FOUND",
                    "버전을 찾을 수 없습니다: " + category + "/" + version);
        }

        List<ContentBlock> current = ordered.get(idx).getBlocks();
        List<ContentBlock> previous = idx > 0 ? ordered.get(idx - 1).getBlocks() : List.of();
        return BlockDiff.diff(previous, current);
    }
```

`NoticeController.java` 상단 import 에 추가:
```java
import kr.co.hakjisa.policyfund.notices.dto.DiffBlock;
import java.util.List;
```
클래스 안에 메서드 추가:
```java
    @GetMapping("/{category}/versions/{version}/diff")
    public List<DiffBlock> diff(@PathVariable String category, @PathVariable String version) {
        return service.diff(category, version);
    }
```

- [ ] **Step 6: diff 엔드포인트 통합 테스트 추가**

`NoticeApiIntegrationTest.java` 클래스 안에 메서드 추가:
```java
    @Test
    void diff_betweenVersions_marksAddAndSame() throws Exception {
        mvc.perform(post("/api/v1/notices/regulation/revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveDate\":\"2026-05-01\",\"blocks\":[{\"type\":\"text\",\"text\":\"공통\"}]}"))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/notices/regulation/revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveDate\":\"2026-06-01\",\"blocks\":[{\"type\":\"text\",\"text\":\"공통\"},{\"type\":\"text\",\"text\":\"추가됨\"}]}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.version").value("v2"));

        mvc.perform(get("/api/v1/notices/regulation/versions/v2/diff"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].type").value("same"))
           .andExpect(jsonPath("$[1].type").value("add"))
           .andExpect(jsonPath("$[1].block.text").value("추가됨"));
    }

    @Test
    void diff_unknownVersion_returns404() throws Exception {
        mvc.perform(get("/api/v1/notices/regulation/versions/v999/diff"))
           .andExpect(status().isNotFound());
    }
```

- [ ] **Step 7: 전체 회귀 확인**

Run: `./gradlew test`
Expected: 모두 PASS (P1 8 tests + P2a 신규 전부).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/notices backend/src/test/java/kr/co/hakjisa/policyfund/notices
git commit -m "$(cat <<'EOF'
feat(backend): add version diff endpoint with LCS block comparison

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 완료 기준 (DoD — P2a)

- `./gradlew test` 전체 PASS(P1 + P2a).
- `GET /api/v1/notices/{regulation|reference}` → 200, 버전 날짜 내림차순. 미지 카테고리 → 404.
- `POST /api/v1/notices/{category}/revisions` → 201, 버전 `vN` 누적(최신이 조회 시 first).
- `GET /api/v1/notices/{category}/versions/{version}/diff` → same/add/del 블록 목록. 미지 버전 → 404.
- 응답이 OpenAPI 스키마(NoticeCategory/NoticeVersion/DiffBlock/ContentBlock)와 일치.

## 다음 계획서 (P2b — 별도)

- **PDF Vision 전처리**(`POST /notices/{category}/revisions/preprocess`): Spring AI(`spring-ai-starter-model-openai`) + PDFBox 의존성, ChatClient 래퍼(목 가능), 업로드 검증(MIME/50MB/UUID 재명명), 텍스트 레이어 우선 + 이미지 페이지만 Vision, 자산 저장 + `GET /api/v1/notices/assets/{uuid}`(콘텐츠 주소화로 발견 #11 충족), PII/프롬프트 인젝션 가드. OpenAI 키 없는 환경에선 ChatClient 목으로 단위 테스트, 컨텍스트 기동용 더미 `spring.ai.openai.api-key` 설정.
