package com.policyfund.search.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyfund.search.embedding.ChunkEmbeddingEntity;
import com.policyfund.search.embedding.ChunkEmbeddingRepository;
import com.policyfund.search.embedding.EmbeddingProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * pipeline 의 chunks.jsonl 한 줄당 한 레코드를 읽어 임베딩을 계산하고 chunk_embedding 에 적재한다.
 * chunk_id 가 PK 이므로 save 는 upsert(멱등)다. embedding_text 가 비면 건너뛴다.
 *
 * 임베딩 계산(느림·네트워크)과 DB 쓰기를 분리한다: {@link #readEntities}로 엔티티를 만들고(트랜잭션 밖),
 * {@link #replaceCategory}로 짧은 트랜잭션 안에서 카테고리 단위 교체(개정본 재색인)를 수행한다.
 */
@Service
public class ChunkIngestService {

    private final EmbeddingProvider embeddingProvider;
    private final ChunkEmbeddingRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChunkIngestService(EmbeddingProvider embeddingProvider, ChunkEmbeddingRepository repository) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
    }

    /** 파일 경로의 jsonl 을 카테고리 태그 없이 적재한다(검색 부트스트랩). 적재 수 반환. */
    public int ingestFile(Path jsonlPath) {
        return ingestFile(jsonlPath, null);
    }

    /** 파일 경로의 jsonl 을 주어진 카테고리 태그로 적재한다. 적재 수 반환. */
    public int ingestFile(Path jsonlPath, String category) {
        List<ChunkEmbeddingEntity> entities = readEntities(jsonlPath, category);
        repository.saveAll(entities);
        return entities.size();
    }

    /** reader 의 jsonl 을 적재한다(카테고리 태그 없음). */
    public int ingestJsonl(BufferedReader reader) {
        List<ChunkEmbeddingEntity> entities = readEntities(reader, null);
        repository.saveAll(entities);
        return entities.size();
    }

    /** jsonl 파일을 파싱·임베딩해 엔티티 목록을 만든다(저장하지 않음). 카테고리는 각 엔티티에 태깅. */
    public List<ChunkEmbeddingEntity> readEntities(Path jsonlPath, String category) {
        try (BufferedReader reader = Files.newBufferedReader(jsonlPath, StandardCharsets.UTF_8)) {
            return readEntities(reader, category);
        } catch (IOException e) {
            throw new UncheckedIOException("chunks.jsonl 적재 실패: " + jsonlPath, e);
        }
    }

    /** reader 의 jsonl 을 파싱·임베딩해 엔티티 목록을 만든다(저장하지 않음). */
    public List<ChunkEmbeddingEntity> readEntities(BufferedReader reader, String category) {
        List<ChunkEmbeddingEntity> entities = new ArrayList<>();
        try {
            String line;
            // seq_no: 적재되는(=DB 에 들어가는) 청크에만 문서 내 0-based 순번을 reading order 로 부여한다.
            // 건너뛴 레코드는 순번을 소비하지 않아 DB 행끼리 연속 seq 를 유지(이웃 확장이 정확).
            int seq = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode record = objectMapper.readTree(line);
                String embeddingText = text(record.get("embedding_text"));
                if (embeddingText == null || embeddingText.isBlank()) {
                    continue;
                }
                entities.add(toEntity(record, embeddingText, seq, category));
                seq++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("chunks.jsonl 파싱 실패", e);
        }
        return entities;
    }

    /**
     * 카테고리 단위 '최신본' 교체: 해당 카테고리의 기존 청크를 모두 지우고 새 청크로 대체한다.
     * 추가로 동일 원본(documentId=콘텐츠 해시)의 기존 청크도 지워, out/ 콜드스타트로 적재된
     * 같은 문서의 category=NULL 중복분을 제거한다(검색 중복·구버전 잔존 방지).
     * 임베딩은 호출 전에 계산해 두고(readEntities), 이 메서드는 짧은 트랜잭션에서 삭제+삽입만 한다.
     */
    @Transactional
    public void replaceCategory(String category, String documentId, List<ChunkEmbeddingEntity> entities) {
        repository.deleteByCategory(category);
        if (documentId != null && !documentId.isBlank()) {
            repository.deleteByDocumentId(documentId);
        }
        repository.saveAll(entities);
    }

    private ChunkEmbeddingEntity toEntity(JsonNode record, String embeddingText, int seqNo, String category) throws IOException {
        String chunkId = text(record.get("chunk_id"));
        String documentId = text(record.get("document_id"));
        String contentType = text(record.get("content_type"));
        JsonNode metadata = record.get("metadata");
        String fileName = metadata != null ? text(metadata.get("file_name")) : null;
        String articleNo = deriveArticleNo(metadata);

        float[] vector = embeddingProvider.embed(embeddingText);
        String embeddingJson = objectMapper.writeValueAsString(vector);

        return new ChunkEmbeddingEntity(chunkId, documentId, fileName, contentType, articleNo, category,
                embeddingText, embeddingJson, vector.length, seqNo, LocalDateTime.now());
    }

    /** heading_path 를 " > " 로 합쳐 article_no 로 쓰고, 없으면 "p."+page_no. */
    private static String deriveArticleNo(JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        JsonNode headingPath = metadata.get("heading_path");
        if (headingPath != null && headingPath.isArray() && !headingPath.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode node : headingPath) {
                String value = text(node);
                if (value != null && !value.isBlank()) {
                    parts.add(value);
                }
            }
            if (!parts.isEmpty()) {
                return String.join(" > ", parts);
            }
        }
        JsonNode pageNo = metadata.get("page_no");
        if (pageNo != null && !pageNo.isNull()) {
            return "p." + pageNo.asText();
        }
        return null;
    }

    private static String text(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }
}
