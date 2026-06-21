package com.policyfund.notices.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 원본 PDF 보관소: 콘텐츠 주소(sha256) 멱등 저장 + 로드 (Docker 불필요). */
class NoticeSourceStorageTest {

    @Test
    void store_isContentAddressed_andLoadsBack(@TempDir Path tmp) {
        NoticeSourceStorage storage = new NoticeSourceStorage(tmp.toString());
        byte[] pdf = "%PDF-1.4 sample bytes".getBytes();

        String id1 = storage.store(pdf);
        String id2 = storage.store(pdf);

        assertThat(id1).matches("[0-9a-f]{64}");
        assertThat(id1).isEqualTo(id2);

        Optional<byte[]> loaded = storage.load(id1);
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(pdf);
    }

    @Test
    void load_invalidOrMissing_returnsEmpty(@TempDir Path tmp) {
        NoticeSourceStorage storage = new NoticeSourceStorage(tmp.toString());
        assertThat(storage.load("not-a-hash")).isEmpty();
        assertThat(storage.load("a".repeat(64))).isEmpty();
    }
}
