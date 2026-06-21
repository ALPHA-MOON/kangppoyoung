package com.policyfund.notices.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 개정본 등록 재색인 입력용 원본 PDF 를 콘텐츠 주소(sha256)로 보관한다.
 * 전처리 단계에서 store → sourceRef(=id) 반환, 등록 시 그 ref 로 load 해 pipeline 재색인 입력으로 쓴다.
 * 동일 PDF → 동일 id 라 중복 저장이 없다.
 */
@Component
public class NoticeSourceStorage {

    private final Path dir;

    public NoticeSourceStorage(@Value("${app.notices.source-dir:./data/notice-src}") String dir) {
        this.dir = Path.of(dir);
    }

    public String store(byte[] pdf) {
        String id = sha256Hex(pdf);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(id + ".pdf");
            if (!Files.exists(file)) {
                Files.write(file, pdf);
            }
            return id;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<byte[]> load(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            return Optional.empty();
        }
        Path file = dir.resolve(id + ".pdf");
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
