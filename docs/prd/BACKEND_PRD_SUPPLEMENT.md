# Backend PRD Supplement — CRITICAL 설계 보완서

> 본 문서는 [Backend PRD](./BACKEND_PRD.md) v0.1에 대한 코드 리뷰에서 도출된  
> **CRITICAL 3건**을 확정된 설계 결정으로 보완한다.  
> 원본 PRD는 수정하지 않으며, 본 문서가 해당 섹션을 Override한다.  
> 버전: v0.1-supplement · 최종 수정: 2026-06-15

---

## 목차

- [C-1. BeanOutputConverter SearchResult 중첩 객체 역직렬화](#c-1)
- [C-2. PDF Vision 파이프라인 — Spring AI Media 한계 및 PDFBox 통합](#c-2)
- [C-3. 예시 질문 5개 제약의 동시성 결함](#c-3)
- [요약 — 확정된 의존성 추가 목록](#summary)

---

<a id="c-1"></a>
## C-1. BeanOutputConverter SearchResult 중첩 객체 역직렬화

### 문제 요약

`BeanOutputConverter<SearchResult>`는 LLM 응답 JSON을 Jackson으로 역직렬화한다.
`duplicateSummary`(중첩 객체)와 `conflicts`(배열)는 OpenAPI 상 optional 필드이며,
LLM이 이를 생략하거나 `null` 문자열로 반환하면 `JsonMappingException`이 발생한다.
일반 POJO 사용 시 기본 생성자와 `@JsonProperty` 부재로 역직렬화가 실패할 수 있다.

### 확정된 설계 결정

#### 1) SearchResult 및 관련 DTO를 Java record로 정의

Java record는 Spring AI `BeanOutputConverter`가 생성자 기반으로 역직렬화할 때 가장 안정적으로 동작한다.
Nullable 필드는 `@Nullable`을 붙이고 `@JsonInclude(NON_NULL)`로 직렬화 시 생략한다.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResultDto(
    @JsonProperty("query")            String query,
    @JsonProperty("answer")           String answer,
    @JsonProperty("evidence")         List<ArticleDto> evidence,
    @JsonProperty("duplicateSummary") @Nullable DuplicateSummaryDto duplicateSummary,
    @JsonProperty("conflicts")        @Nullable List<ArticleDto> conflicts
) {}

public record DuplicateSummaryDto(
    @JsonProperty("summary") String summary,
    @JsonProperty("sources") List<ArticleDto> sources
) {}

public record ArticleDto(
    @JsonProperty("docId") String docId,
    @JsonProperty("docTitle") String docTitle,
    @JsonProperty("docType") String docType,
    @JsonProperty("articleNo") String articleNo,
    @JsonProperty("text") String text
) {}
```

#### 2) BeanOutputConverter 사용 패턴

```java
@Service
public class SearchAiService {
    private final ChatClient chatClient;
    private final BeanOutputConverter<SearchResultDto> outputConverter;

    public SearchAiService(ChatClient.Builder b) {
        this.chatClient = b.build();
        this.outputConverter = new BeanOutputConverter<>(SearchResultDto.class);
    }

    public SearchResultDto synthesize(String query, List<ArticleDto> candidates) {
        String format = outputConverter.getFormat();
        String prompt = String.format(
            "[질의]
%s

[후보 조항]
%s

[출력 형식]
%s",
            query, candidates, format);
        String raw = chatClient.prompt().user(prompt).call().content();
        return outputConverter.convert(raw);
    }
}
```

#### 3) OutputParserException 전역 처리 — @RestControllerAdvice

LLM이 형식을 위반한 JSON을 반환하면 `OutputParserException`이 발생한다. 이를 502로 변환한다.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OutputParserException.class)
    public ResponseEntity<ErrorDto> handleOutputParser(OutputParserException ex) {
        log.error("LLM 출력 파싱 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorDto("AI_PARSE_ERROR", "AI 응답을 해석하지 못했습니다. 잠시 후 재시도하세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorDto("VALIDATION_ERROR", message));
    }
}
```

#### 4) 프롬프트 파일 외부화

프롬프트는 코드에 하드코딩하지 않고 리소스 파일로 관리하여 Git에서 diff 추적한다.

```
src/main/resources/prompts/
  search-synthesize.st
  ranking-categorize.st
```

```java
@Value("classpath:prompts/search-synthesize.st")
private Resource searchPromptTemplate;
```

### BACKEND_PRD.md 갱신 안내

| 섹션 | 변경 내용 |
|------|-----------|
| §7 AI 연동 "답변 종합" 행 | Java record DTO + `@JsonInclude(NON_NULL)` 사용, `OutputParserException` → 502 주석 추가 |
| §7 운영 고려 | LLM 파싱 실패는 `@RestControllerAdvice`에서 502로 변환 항목 추가 |
| §4.2 Integration 계층 | 프롬프트는 `src/main/resources/prompts/*.st`로 버전 관리 추가 |
| §11 디렉토리 구조 | `src/main/resources/prompts/` 디렉토리 항목 추가 |

---

<a id="c-2"></a>
## C-2. PDF Vision 파이프라인 — Spring AI Media 한계 및 PDFBox 통합

### 문제 요약

Spring AI 1.0의 `Media` 클래스는 `image/*` MIME 타입(PNG, JPEG, WebP, GIF)만 지원한다.
`application/pdf`를 OpenAI Vision에 직접 전달하는 경로는 존재하지 않는다.
PDF 페이지를 이미지화하는 라이브러리, 페이지 분할 전략, 타임아웃 처리가 기술 스택에 빠져 있다.

### 확정된 설계 결정

#### 1) 파이프라인 전체 흐름

```
[multipart PDF 업로드] -> [PDFBox: 페이지별 PNG, 최대 20페이지]
-> [이미지 저장: /app/uploads/, UUID 파일명] -> [ChatClient Media[] 배치]
-> [ContentBlock[] 파싱] -> [응답 반환 - 등록 미확정]
```

#### 2) 추가 의존성

```groovy
dependencies {
    implementation 'org.apache.pdfbox:pdfbox:3.0.2'
}
```

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.2</version>
</dependency>
```

#### 3) PDF 변환 컴포넌트

```java
@Component
public class PdfImageConverter {
    public List<byte[]> toPngPages(byte[] pdf, int max) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer r = new PDFRenderer(doc);
            int n = Math.min(doc.getNumberOfPages(), max);
            List<byte[]> pages = new ArrayList<>(n);
            for (int i=0; i<n; i++) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(r.renderImageWithDPI(i,150), "PNG", baos);
                pages.add(baos.toByteArray());
            }
            return pages;
        }
    }
}
```

#### 4) Vision 호출 서비스

```java
@Service
public class PdfVisionService {
    private static final int MAX=20, CHUNK=5;
    // @Qualifier("visionChatClient") 주입
    private final ChatClient visionChatClient;
    private final PdfImageConverter converter;
    private final BeanOutputConverter<ContentBlockListDto> outputConverter;

    public List<ContentBlockDto> preprocess(byte[] pdf) throws IOException {
        List<byte[]> pages = converter.toPngPages(pdf, MAX);
        if (pages.isEmpty()) throw new IllegalArgumentException("페이지 추출 불가.");
        List<ContentBlockDto> result = new ArrayList<>();
        for (int o=0; o<pages.size(); o+=CHUNK)
            result.addAll(callChunk(pages.subList(o,Math.min(o+CHUNK,pages.size()))));
        return result;
    }

    private List<ContentBlockDto> callChunk(List<byte[]> chunk) {
        List<Media> media = chunk.stream()
            .map(b -> new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(b))).toList();
        UserMessage msg = new UserMessage("ContentBlock 배열 추출.
" + outputConverter.getFormat(), media);
        return outputConverter.convert(visionChatClient.prompt().messages(msg).call().content()).blocks();
    }
}
```

#### 5) 이미지 저장 추상화 (1차: 로컨 볼륨, 추후 S3 교체 가능)

```java
public interface UploadFileStore { String store(byte[] data, String ext); }

@Component
@ConditionalOnProperty(name="app.storage.type",havingValue="local",matchIfMissing=true)
public class LocalUploadFileStore implements UploadFileStore {
    @Value("${app.storage.local.base-path:/app/uploads}") private String base;
    @Value("${app.storage.local.url-prefix:/uploads}")    private String prefix;
    public String store(byte[] data, String ext) {
        String f = UUID.randomUUID()+"."+ext;
        try { Files.createDirectories(Paths.get(base)); Files.write(Paths.get(base,f),data); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        return prefix+"/"+f;
    }
}
```

S3 전환 시 `@ConditionalOnProperty(havingValue="s3")` 구현체만 추가하면 된다.

#### 6) Vision 전용 ChatClient 빈 — 타임아웃 분리

일반 검색(30초)과 PDF Vision(120초)은 타임아웃이 다르므로 별도 빈으로 분리한다.
실제 구현 시 `OpenAiChatOptions` + `RestClientCustomizer`로 `ReadTimeout`을 각각 설정한다.

```java
@Configuration
public class AiConfig {
    @Bean("searchChatClient")
    public ChatClient searchChatClient(ChatModel m) { return ChatClient.builder(m).build(); }
    @Bean("visionChatClient")
    public ChatClient visionChatClient(ChatModel m) {
        // TODO: RestClientCustomizer 로 ReadTimeout=120s 설정한 별도 인스턴스 주입
        return ChatClient.builder(m).build();
    }
}
```

#### 7) application.yml 추가 설정

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
app:
  storage:
    type: local
    local:
      base-path: /app/uploads
      url-prefix: /uploads
  ai:
    vision:
      max-pdf-pages: 20
      chunk-size: 5
      timeout-seconds: 120
    search:
      timeout-seconds: 30
```

#### 8) nginx 설정

```nginx
client_max_body_size 55M;
```

#### 9) Docker Compose 볼륨 마운트

```yaml
services:
  backend:
    volumes:
      - uploads_data:/app/uploads
volumes:
  uploads_data:
```

### BACKEND_PRD.md 갱신 안내

| 섹션 | 변경 내용 |
|------|-----------|
| §3 기술 스택 표 | PDF 변환 행 추가: `Apache PDFBox 3.x`, PDF → PNG 변환 용도 |
| §7 AI 연동 PDF Vision 행 | PDFBox 변환 → 5페이지 청크 → `Media[]` 멀티모달 흐름 주석 추가 |
| §8 비기능 요구사항 | 파일 업로드 50MB, nginx `client_max_body_size 55M` 항목 추가 |
| §10 배포 | Docker Compose `uploads_data` 볼륨 마운트 항목 추가 |
| §13 미정 | PDF Vision 표·도표 포맷 항목 제거 — 확정: `type:image` 블록 |

---

<a id="c-3"></a>
## C-3. 예시 질문 5개 제약의 동시성 결함

### 문제 요약

"개수 조회 → 조건 분기 → 삽입"을 단순 `@Transactional`로 감싸면
MySQL REPEATABLE READ에서 두 동시 요청이 모두 COUNT=4를 읽고 각각 삽입하여 6개가 된다.
비관적 잠금 또는 DB 레벨 제약이 없어 5개 초과가 발생할 수 있다.

### 확정된 설계 결정

서비스 계층(비관적 잠금)과 DB 계층(트리거) 두 계층에서 이중으로 방어한다.

#### 전략 1 — 서비스 계층: SELECT ... FOR UPDATE

```java
public interface SearchExampleRepository extends JpaRepository<SearchExample, Long> {
    @Query(value="SELECT COUNT(*) FROM search_example FOR UPDATE", nativeQuery=true)
    long countWithLock();
}
```

```java
@Service @RequiredArgsConstructor
public class SearchExampleService {
    private static final int MAX=5;
    private final SearchExampleRepository repo;

    @Transactional
    public SearchExampleDto addExample(String text) {
        if (repo.countWithLock()>=MAX)
            throw new ExampleLimitExceededException("예시 질문은 최대 "+MAX+"개까지 등록할 수 있습니다.");
        return SearchExampleDto.from(repo.save(new SearchExample(text)));
    }

    @Transactional(readOnly=true)
    public List<SearchExampleDto> listExamples() {
        return repo.findAll().stream().map(SearchExampleDto::from).toList();
    }

    @Transactional
    public void deleteExample(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("미존재: "+id);
        repo.deleteById(id);
    }
}
```

```java
public class ExampleLimitExceededException extends RuntimeException {
    public ExampleLimitExceededException(String m) { super(m); }
}

// GlobalExceptionHandler 추가
@ExceptionHandler(ExampleLimitExceededException.class)
public ResponseEntity<ErrorDto> handleExampleLimit(ExampleLimitExceededException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorDto("EXAMPLE_LIMIT_EXCEEDED", ex.getMessage()));
}
```

#### 전략 2 — DB 계층: BEFORE INSERT 트리거 (이중 방어)

MySQL `CHECK` 제약은 단일 행 값만 평가하므로 테이블 전체 행 수 제약에는
`BEFORE INSERT` 트리거를 사용한다.

```sql
-- db/migration/V2__search_example_limit_trigger.sql
DELIMITER $$

CREATE TRIGGER trg_search_example_max_rows
BEFORE INSERT ON search_example FOR EACH ROW
BEGIN
    DECLARE cnt INT;
    SELECT COUNT(*) INTO cnt FROM search_example;
    IF cnt >= 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'search_example: maximum 5 rows allowed';
    END IF;
END$$

DELIMITER ;
```

트리거 발화 시 `DataIntegrityViolationException`이 던져지며
`@RestControllerAdvice`에서 409로 변환한다.

```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException ex) {
    String msg = ex.getMostSpecificCause().getMessage();
    if (msg!=null && msg.contains("maximum 5 rows allowed"))
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("EXAMPLE_LIMIT_EXCEEDED","예시 질문은 최대 5개까지 등록할 수 있습니다."));
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorDto("DATA_INTEGRITY_ERROR","데이터 무결성 오류가 발생했습니다."));
}
```

#### 잠금 전략 결정 근거

| 방식 | 체택 여부 | 근거 |
|------|-----------|------|
| `SELECT...FOR UPDATE` (비관적 잠금) | 체택 | 집계 카운트 필요한 low-write 시나리오, 데드락 위험 낙음 |
| 낙관적 잠금 (`@Version`) | 미체택 | 단일 행 버전 컬럼으로 테이블 전체 집계 제약 불가 |
| DB TRIGGER | 이중 방어 체택 | MySQL CHECK 한계 보완, 서비스 우회 직접 INSERT 방어 |

### BACKEND_PRD.md 갱신 안내

| 섹션 | 변경 내용 |
|------|-----------|
| §5.1 수용 기준 | 서비스 `FOR UPDATE` 비관적 잠금 + DB 트리거 이중 방어 주석 추가 |
| §6 데이터 모델 | `search_example` 행 수 제약: `FOR UPDATE` + Flyway V2 `BEFORE INSERT` 트리거로 변경 |
| §12 구현 계획 P1 | Flyway V1 스키마 + V2 search_example 트리거 항목 추가 |

---

<a id="summary"></a>
## 요약 — 확정된 의존성 추가 목록

### 빌드 의존성 (신규)

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| `org.apache.pdfbox:pdfbox` | 3.0.2 | PDF → PNG 페이지 변환 (C-2) |

### application.yml 추가 키 전체

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
app:
  storage:
    type: local
    local:
      base-path: /app/uploads
      url-prefix: /uploads
  ai:
    vision:
      max-pdf-pages: 20
      chunk-size: 5
      timeout-seconds: 120
    search:
      timeout-seconds: 30
```

### Flyway 마이그레이션 파일

| 파일 | 내용 |
|------|------|
| `V1__init_schema.sql` | 전체 스키마 DDL (`search_example` 포함) |
| `V2__search_example_limit_trigger.sql` | `trg_search_example_max_rows` 트리거 (C-3) |

### 인프라 추가 설정

| 대상 | 변경 내용 |
|------|-----------|
| `nginx/nginx.conf` | `client_max_body_size 55M` 추가 (C-2) |
| `docker-compose.yml` | `backend` 서비스에 `uploads_data:/app/uploads` 볼륨 마운트 (C-2) |

---

*본 문서는 BACKEND_PRD.md의 CRITICAL 항목만 보완한다.
HIGH/MEDIUM 항목 보완은 별도 문서로 작성한다.*
