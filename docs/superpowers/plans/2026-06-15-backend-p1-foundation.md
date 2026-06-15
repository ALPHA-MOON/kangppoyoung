# Backend P1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 정책자금 백엔드의 실행 가능한 토대(Spring Boot 골격 + MySQL/Flyway 스키마 + 전역 에러 처리 + 보안 골격 + Docker Compose/nginx)를 만든다. 비즈니스 엔드포인트는 P2~P4에서 추가한다.

**Architecture:** Java 21 + Spring Boot 3.4 단일 모듈. Controller→Service→Repository 레이어. MySQL 8을 영구 저장소로 두고 Flyway로 전체 스키마를 1차에 생성. 전역 `@RestControllerAdvice`가 모든 오류를 `Error{code,message}`로 통일. Spring Security는 1차 `permitAll()` 골격(엔드포인트 위험도 분류는 주석으로 예약). nginx 리버스 프록시 + Docker Compose(nginx+backend+mysql).

**Tech Stack:** Java 21, Spring Boot 3.4.5, Gradle, Spring Data JPA, Flyway(+flyway-mysql), MySQL 8, Spring Security, Spring Boot Actuator, JUnit 5, Testcontainers(MySQL), MockMvc.

**관련 문서:** [BACKEND_PRD](../../prd/BACKEND_PRD.md) · [backend_user_flow](../../user_flow/backend_user_flow.md) · [OpenAPI](../../api/openapi.yaml)

**제약(반드시 준수):**
- 신규 코드는 모두 `backend/`(및 루트의 `docker-compose.yml`, `nginx/`)에 둔다.
- `docs/`, `frontend/`, `docs/api/openapi.yaml` 등 **기존 파일은 절대 수정하지 않는다.**
- 패키지 루트: `kr.co.hakjisa.policyfund`
- 커밋은 각 Task 끝에서. 커밋 메시지 영어, 본문 마지막 줄에 다음을 포함:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Windows 환경: 명령은 Git Bash 기준 `./gradlew`. PowerShell이면 `.\gradlew.bat`로 치환.
- Testcontainers 테스트는 **Docker Desktop이 실행 중**이어야 한다.

---

## File Structure

생성 파일(모두 신규):

```
backend/
  settings.gradle
  build.gradle
  gradlew, gradlew.bat, gradle/wrapper/*        # gradle wrapper 생성물
  Dockerfile
  src/main/java/kr/co/hakjisa/policyfund/
    PolicyFundApplication.java                   # 부트 진입점
    config/SecurityConfig.java                   # Security 필터 체인 골격
    common/error/ErrorResponse.java              # {code,message} (OpenAPI Error)
    common/error/ApiException.java               # 상태/코드 보유 기본 예외
    common/error/ResourceNotFoundException.java  # 404
    common/error/ConflictException.java          # 409
    common/error/BadRequestException.java        # 400
    common/error/GlobalExceptionHandler.java     # @RestControllerAdvice
  src/main/resources/
    application.yml                              # 공통(에러/액추에이터 하드닝)
    application-local.yml                        # 로컬 datasource(시크릿 없음, env 플레이스홀더)
    application-docker.yml                       # 컨테이너 datasource
    db/migration/V1__init_schema.sql            # 전체 MySQL 스키마
  src/test/java/kr/co/hakjisa/policyfund/
    support/AbstractIntegrationTest.java         # Testcontainers MySQL 베이스
    FlywaySchemaIntegrationTest.java             # 스키마 생성 검증
    ActuatorHardeningIntegrationTest.java        # 액추에이터 노출 최소화 검증
    common/error/GlobalExceptionHandlerTest.java # @WebMvcTest 에러 매핑
    config/SecurityConfigTest.java               # permitAll + CSRF 비활성 검증
docker-compose.yml                               # 루트
nginx/nginx.conf                                 # 루트
.gitignore                                       # 루트(신규)
.env.example                                     # 루트(키 목록만)
```

> **시크릿 정책 메모:** `application-local.yml`은 `${DB_USERNAME:policyfund}` 같은 **env 플레이스홀더만** 담고 실제 시크릿이 없으므로 커밋한다. 실제 시크릿은 루트 `.env`(gitignore)로만 주입한다. BACKEND_PRD §9.4의 "application-local.yml gitignore" 권고는 그 파일이 시크릿을 가질 때를 전제하므로, 시크릿 0 정책을 지키는 본 구성에서는 커밋이 안전하다.

---

## Task 1: Gradle 프로젝트 골격 + 부트 진입점

**Files:**
- Create: `backend/settings.gradle`
- Create: `backend/build.gradle`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/PolicyFundApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/application-docker.yml`
- Generate: `backend/gradlew`, `backend/gradle/wrapper/*`

- [ ] **Step 1: settings.gradle 작성**

`backend/settings.gradle`:
```groovy
rootProject.name = 'policyfund-backend'
```

- [ ] **Step 2: build.gradle 작성**

`backend/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'kr.co.hakjisa'
version = '0.1.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: 부트 진입점 작성**

`backend/src/main/java/kr/co/hakjisa/policyfund/PolicyFundApplication.java`:
```java
package kr.co.hakjisa.policyfund;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PolicyFundApplication {
    public static void main(String[] args) {
        SpringApplication.run(PolicyFundApplication.class, args);
    }
}
```

- [ ] **Step 4: 설정 파일 작성**

`backend/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: policyfund-backend
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
  flyway:
    enabled: true

server:
  port: 8080
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

`backend/src/main/resources/application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/policyfund?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: ${DB_USERNAME:policyfund}
    password: ${DB_PASSWORD:policyfund}
```

`backend/src/main/resources/application-docker.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/policyfund?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

- [ ] **Step 5: Gradle wrapper 생성**

Run (gradle 설치되어 있을 때): `cd backend && gradle wrapper --gradle-version 8.10.2`
Gradle 미설치 시 대안: 임시로 `gradle:8.10.2-jdk21` 이미지를 사용
`docker run --rm -v "$PWD/backend":/app -w /app gradle:8.10.2-jdk21 gradle wrapper --gradle-version 8.10.2`
Expected: `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` 생성.

- [ ] **Step 6: 컴파일 검증**

Run: `cd backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. (아직 테스트 없음, datasource 없이도 컴파일만 수행.)

- [ ] **Step 7: Commit**

```bash
git add backend/settings.gradle backend/build.gradle backend/gradlew backend/gradlew.bat backend/gradle backend/src/main
git commit -m "$(cat <<'EOF'
chore(backend): scaffold Spring Boot 3.4 project skeleton

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Flyway 전체 스키마 + Testcontainers 통합 베이스

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `backend/src/test/java/kr/co/hakjisa/policyfund/support/AbstractIntegrationTest.java`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/FlywaySchemaIntegrationTest.java`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

먼저 Testcontainers 베이스:
`backend/src/test/java/kr/co/hakjisa/policyfund/support/AbstractIntegrationTest.java`:
```java
package kr.co.hakjisa.policyfund.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("policyfund");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

테스트:
`backend/src/test/java/kr/co/hakjisa/policyfund/FlywaySchemaIntegrationTest.java`:
```java
package kr.co.hakjisa.policyfund;

import kr.co.hakjisa.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationCreatesAllCoreTables() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_name IN " +
                "('policy_document','article','search_history','search_example'," +
                "'notice_category','notice_version','ranking_cache')",
                Integer.class, MYSQL.getDatabaseName());
        assertThat(count).isEqualTo(7);
    }

    @Test
    void articleHasNgramFulltextIndex() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = ? AND table_name = 'article' " +
                "AND index_name = 'ft_article_text'",
                Integer.class, MYSQL.getDatabaseName());
        assertThat(count).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "*FlywaySchemaIntegrationTest"`
Expected: FAIL — 컨텍스트 시작 실패(마이그레이션 파일 없음) 또는 테이블 0개.

- [ ] **Step 3: Flyway 스키마 작성**

`backend/src/main/resources/db/migration/V1__init_schema.sql`:
```sql
CREATE TABLE policy_document (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    title            VARCHAR(500) NOT NULL,
    type             VARCHAR(10)  NOT NULL,
    updated_at       DATE         NOT NULL,
    is_single_source BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doc_id     VARCHAR(64)  NOT NULL,
    doc_title  VARCHAR(500) NOT NULL,
    doc_type   VARCHAR(10)  NOT NULL,
    article_no VARCHAR(100) NOT NULL,
    `text`     TEXT         NOT NULL,
    CONSTRAINT fk_article_doc FOREIGN KEY (doc_id) REFERENCES policy_document(id),
    FULLTEXT INDEX ft_article_text (`text`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE search_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    query       VARCHAR(500) NOT NULL,
    answer      TEXT         NULL,
    result_json JSON         NULL,
    created_at  DATETIME(6)  NOT NULL,
    INDEX idx_search_history_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- search_example: 슬롯(0~4) 유니크로 최대 5개 동시성 안전 보장 (BACKEND_PRD 발견 #4)
CREATE TABLE search_example (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    slot       TINYINT      NOT NULL,
    `text`     VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_search_example_slot UNIQUE (slot),
    CONSTRAINT ck_search_example_slot CHECK (slot BETWEEN 0 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_category (
    `key`     VARCHAR(20)  NOT NULL PRIMARY KEY,
    label     VARCHAR(100) NOT NULL,
    doc_title VARCHAR(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_version (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_key VARCHAR(20) NOT NULL,
    version      VARCHAR(20) NOT NULL,
    `date`       DATE        NOT NULL,
    blocks_json  JSON        NOT NULL,
    CONSTRAINT fk_version_category FOREIGN KEY (category_key) REFERENCES notice_category(`key`),
    CONSTRAINT uq_version UNIQUE (category_key, version),
    INDEX idx_version_date (category_key, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ranking_cache (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    period                VARCHAR(20)  NOT NULL,
    category              VARCHAR(255) NOT NULL,
    question_example      VARCHAR(500) NULL,
    search_count          INT          NOT NULL DEFAULT 0,
    view_count            INT          NOT NULL DEFAULT 0,
    trend                 VARCHAR(10)  NOT NULL,
    related_articles_json JSON         NULL,
    computed_at           DATETIME(6)  NOT NULL,
    INDEX idx_ranking_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*FlywaySchemaIntegrationTest"`
Expected: PASS (7개 테이블 + ngram FULLTEXT 인덱스 확인). Docker Desktop 실행 필요.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/test
git commit -m "$(cat <<'EOF'
feat(backend): add Flyway initial schema and Testcontainers base

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 전역 에러 처리 (`Error{code,message}`)

**Files:**
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/ErrorResponse.java`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/ApiException.java`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/ResourceNotFoundException.java`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/ConflictException.java`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/BadRequestException.java`
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/common/error/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/kr/co/hakjisa/policyfund/common/error/GlobalExceptionHandlerTest.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@Import({GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @RestController
    static class TestController {
        @GetMapping("/_test/notfound")
        public void notFound() { throw new ResourceNotFoundException("NOT_FOUND", "리소스가 없습니다"); }

        @GetMapping("/_test/boom")
        public void boom() { throw new IllegalStateException("내부 디테일 노출 금지"); }

        @PostMapping("/_test/validate")
        public void validate(@RequestBody @Valid Payload payload) { }

        record Payload(@NotBlank String name) {}
    }

    @Test
    void resourceNotFound_returns404WithCode() throws Exception {
        mvc.perform(get("/_test/notfound"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NOT_FOUND"))
           .andExpect(jsonPath("$.message").value("리소스가 없습니다"));
    }

    @Test
    void validationError_returns400() throws Exception {
        mvc.perform(post("/_test/validate")
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void uncaught_returns500WithFixedMessage() throws Exception {
        mvc.perform(get("/_test/boom"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
           .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "*GlobalExceptionHandlerTest"`
Expected: FAIL — `ErrorResponse`/`ResourceNotFoundException`/`GlobalExceptionHandler` 미정의로 컴파일 에러.

- [ ] **Step 3: 구현 작성**

`ErrorResponse.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

/** OpenAPI Error 스키마(code, message)와 1:1. */
public record ErrorResponse(String code, String message) {}
```

`ApiException.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
```

`ResourceNotFoundException.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
```

`ConflictException.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
```

`BadRequestException.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {
    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
```

`GlobalExceptionHandler.java`:
```java
package kr.co.hakjisa.policyfund.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("잘못된 요청입니다");
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaught(Exception ex) {
        // 원인은 서버 로그에만 기록한다. 클라이언트에는 고정 메시지만 반환(BACKEND_PRD §9.5).
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*GlobalExceptionHandlerTest"`
Expected: PASS (3개 테스트).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/common backend/src/test/java/kr/co/hakjisa/policyfund/common
git commit -m "$(cat <<'EOF'
feat(backend): add global error handling with Error{code,message}

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Spring Security 골격 (1차 permitAll + CSRF 비활성)

**Files:**
- Create: `backend/src/main/java/kr/co/hakjisa/policyfund/config/SecurityConfig.java`
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/config/SecurityConfigTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/kr/co/hakjisa/policyfund/config/SecurityConfigTest.java`:
```java
package kr.co.hakjisa.policyfund.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.DummyController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @RestController
    static class DummyController {
        @PostMapping("/api/v1/_dummy")
        public String dummy() { return "ok"; }
    }

    @Test
    void postIsAllowedWithoutAuthOrCsrf() throws Exception {
        // permitAll + CSRF 비활성이면 인증/CSRF 토큰 없이도 200.
        // (CSRF 활성 시 403, 인증 필요 시 401/403)
        mvc.perform(post("/api/v1/_dummy"))
           .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests "*SecurityConfigTest"`
Expected: FAIL — `SecurityConfig` 미정의(컴파일 에러). (정의 전 기본 보안은 CSRF로 POST 403이 되어도 실패로 간주.)

- [ ] **Step 3: 구현 작성**

`SecurityConfig.java`:
```java
package kr.co.hakjisa.policyfund.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * 1차: 전 엔드포인트 permitAll. API는 무상태이므로 CSRF 비활성.
     * 추후 인증/RBAC 도입 시 아래 분류대로 권한을 부여한다(BACKEND_PRD §9.1).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // 변경성(고위험) — 추후 hasRole("ADMIN")
                //   POST /api/v1/notices/*/revisions
                //   POST /api/v1/notices/*/revisions/preprocess
                // 변경성 — 추후 hasRole("MANAGER")
                //   POST/DELETE /api/v1/search/examples/**
                // 민감 조회 — 추후 hasRole("MANAGER")
                //   GET /api/v1/search/history
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew test --tests "*SecurityConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/kr/co/hakjisa/policyfund/config backend/src/test/java/kr/co/hakjisa/policyfund/config
git commit -m "$(cat <<'EOF'
feat(backend): add Spring Security skeleton (permitAll, CSRF off)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 액추에이터 노출 최소화 검증

> 설정(`management.*`, `server.error.*`)은 Task 1의 `application.yml`에 이미 있다. 이 Task는 그 하드닝이 실제로 적용됨을 통합 테스트로 고정한다(BACKEND_PRD §9.5/§9.6).

**Files:**
- Test: `backend/src/test/java/kr/co/hakjisa/policyfund/ActuatorHardeningIntegrationTest.java`

- [ ] **Step 1: 테스트 작성**

`backend/src/test/java/kr/co/hakjisa/policyfund/ActuatorHardeningIntegrationTest.java`:
```java
package kr.co.hakjisa.policyfund;

import kr.co.hakjisa.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorHardeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void health_isUp_withoutComponentDetails() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
        assertThat(res.getBody()).doesNotContain("components");
    }

    @Test
    void env_endpoint_isNotExposed() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/env", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 2: 테스트 실행(통과 확인)**

Run: `cd backend && ./gradlew test --tests "*ActuatorHardeningIntegrationTest"`
Expected: PASS — health는 200(UP, components 없음), env는 404. (실패 시 Task 1 `application.yml`의 `management.*`/`show-details` 확인.)

- [ ] **Step 3: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: 모든 테스트 PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/kr/co/hakjisa/policyfund/ActuatorHardeningIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(backend): lock down actuator exposure (health only, no details)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Docker Compose + Dockerfile + nginx + 시크릿 파일

**Files:**
- Create: `backend/Dockerfile`
- Create: `docker-compose.yml` (루트)
- Create: `nginx/nginx.conf` (루트)
- Create: `.gitignore` (루트)
- Create: `.env.example` (루트)

- [ ] **Step 1: .gitignore 작성**

루트 `.gitignore`:
```gitignore
# Build
backend/build/
backend/.gradle/

# Secrets (실제 시크릿은 절대 커밋하지 않음 — BACKEND_PRD §9.4)
.env
*.env
!.env.example

# IDE
.idea/
*.iml
.vscode/
```

- [ ] **Step 2: .env.example 작성 (키 목록만, 실제 값 없음)**

루트 `.env.example`:
```dotenv
# DB 자격증명 (docker-compose 가 backend·mysql 에 주입)
DB_USERNAME=policyfund
DB_PASSWORD=change-me
DB_ROOT_PASSWORD=change-me-root

# OpenAI (P3 부터 사용 — 지금은 미설정이어도 됨)
OPENAI_API_KEY=
```

- [ ] **Step 3: Dockerfile 작성 (멀티스테이지)**

`backend/Dockerfile`:
```dockerfile
# --- build stage ---
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon -x test

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# 시크릿은 ENV/ARG 로 굽지 않는다. 런타임에 env_file 로 주입.
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 4: nginx.conf 작성**

`nginx/nginx.conf`:
```nginx
# 레이트리밋 존(BACKEND_PRD §9.3). 대상 엔드포인트는 P2/P3 에서 활성.
limit_req_zone $binary_remote_addr zone=search:10m rate=20r/m;
limit_req_zone $binary_remote_addr zone=preprocess:10m rate=5r/m;

upstream backend {
    server backend:8080;
}

server {
    listen 80;
    client_max_body_size 50m;   # PDF 업로드 50MB 허용(BACKEND_PRD 발견 #9)

    # 변경성·민감 조회 경로의 사내 IP 제한(BACKEND_PRD §9.2).
    # 운영 대역에 맞게 allow 값을 조정한다.
    location ~ ^/api/v1/notices/[^/]+/revisions {
        limit_req zone=preprocess burst=3 nodelay;
        allow 10.0.0.0/8;
        deny all;
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location = /api/v1/search {
        limit_req zone=search burst=10 nodelay;
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /api/v1/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location = /actuator/health {
        proxy_pass http://backend;
    }
}
```

- [ ] **Step 5: docker-compose.yml 작성**

루트 `docker-compose.yml`:
```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: policyfund
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${DB_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10

  backend:
    build: ./backend
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      mysql:
        condition: service_healthy
    expose:
      - "8080"

  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  mysql-data:
```

- [ ] **Step 6: 로컬 .env 생성 후 스택 기동 검증**

Run:
```bash
cp .env.example .env
# .env 의 DB_PASSWORD/DB_ROOT_PASSWORD 를 임의 값으로 설정 후:
docker compose up -d --build
```
Expected: `mysql`, `backend`, `nginx` 세 컨테이너 healthy/started.

- [ ] **Step 7: nginx 경유 헬스체크 확인**

Run: `curl -i http://localhost/actuator/health`
Expected: `200 OK`, 본문 `{"status":"UP"}` (components 없음). 실패 시 `docker compose logs backend` 확인.

- [ ] **Step 8: 스택 정리**

Run: `docker compose down`
Expected: 컨테이너 정지·제거(볼륨 `mysql-data` 는 유지).

- [ ] **Step 9: Commit**

```bash
git add .gitignore .env.example docker-compose.yml nginx/nginx.conf backend/Dockerfile
git commit -m "$(cat <<'EOF'
feat(backend): add Docker Compose, Dockerfile and nginx reverse proxy

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 완료 기준 (Definition of Done — P1)

- `cd backend && ./gradlew test` 전체 PASS(Docker Desktop 실행 상태).
- `docker compose up -d --build` 후 `curl http://localhost/actuator/health` → `200 {"status":"UP"}`.
- 7개 테이블 + `article` ngram FULLTEXT 인덱스가 Flyway로 생성됨.
- 모든 오류가 `Error{code,message}`로 반환, 5xx는 고정 메시지.
- Security 골격(permitAll, CSRF off) 동작, 위험도 분류는 주석으로 예약.
- `.env`는 gitignore, 저장소엔 `.env.example`만.

---

## 다음 계획서 (별도 작성 예정)

- **P2 — notices:** `GET /notices/{category}`, `GET .../versions/{version}/diff`(LCS 블록 비교 + 이미지 콘텐츠 해시 동등성, 발견 #11), `POST .../revisions`, `POST .../revisions/preprocess`(업로드 검증·PDFBox 텍스트 우선·이미지 페이지만 Spring AI Vision). 엔티티/리포지토리/`ContentBlock` JSON 매핑(발견 #7) 포함.
- **P3 — search:** `POST /search`(RetrievalPort+MySQL FULLTEXT → Spring AI ChatClient 구조화 출력, 발견 #5), 이력·예시(슬롯 유니크 동시성, 발견 #4), PII 마스킹·프롬프트 인젝션 방어·query 500자 Controller 검증.
- **P4 — rankings/onboarding:** `GET /rankings`(카테고리화+캐시), `GET /onboarding`(랭킹 환산, period 선택+기본값).

---

## 실제 구현 메모 (P1 적용 결과 — 환경 강제 변경)

> 이 환경에 맞춰 계획 대비 다음이 조정되었다. **P2~P4 실행자는 이 값을 기준으로 한다.**

- **Java 21 → 25 (LTS):** 이 머신엔 JDK 25 LTS만 설치. Gradle 8.10.2는 Java 25에서 실행 불가, foojay 0.9.0은 Gradle 9에서 깨짐 → toolchain을 **Java 25**로, 둘 다 LTS.
- **Gradle 8.10.2 → 9.5.1 (wrapper):** Java 25 런타임 지원 위해.
- **Testcontainers Docker 연결(머신 한정, 비커밋):** 이 머신의 Docker Desktop은 docker-java가 API 버전을 1.40 미만으로 낮춰 빈 ServerVersion을 받아 거부됨. 해결: 비커밋 `~/.gradle/init.gradle`(테스트 JVM에 `systemProperty 'api.version','1.54'` + `environment 'DOCKER_HOST','tcp://localhost:2375'`) + `~/.testcontainers.properties`(`docker.host=tcp://localhost:2375`). **레포에는 머신 설정을 넣지 않는다.**
- **통합 테스트 베이스:** 여러 `@Testcontainers` 클래스가 한 JVM에서 정적 컨테이너를 공유할 때 stop 경쟁이 발생 → `AbstractIntegrationTest`는 **싱글톤 컨테이너 패턴**(static 블록 start, Ryuk 정리) 사용. 새 통합 테스트는 이 베이스를 상속만 하면 된다.
- **전역 에러 핸들러:** 미등록 경로가 500이 되지 않도록 `@ExceptionHandler(NoResourceFoundException.class) → 404`가 catch-all 앞에 추가되어 있다.
- **nginx:** 시작 레이스(`host not found in upstream`) 방지를 위해 `upstream` 대신 **Docker DNS resolver(127.0.0.11) + 변수 proxy_pass** 사용. 호스트 포트는 **`NGINX_PORT`(기본 80)**로 파라미터화(로컬에서 80 점유 시 오버라이드).
- **Dockerfile:** `eclipse-temurin:25-jdk`(빌드, gradle wrapper bootJar) → `eclipse-temurin:25-jre`(런타임).
- **검증 완료:** `./gradlew test` 8 tests GREEN; `docker compose up` 후 nginx 경유 `/actuator/health` → `200 {"status":"UP"}`.
