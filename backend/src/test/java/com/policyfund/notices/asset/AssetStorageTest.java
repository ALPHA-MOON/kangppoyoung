package com.policyfund.notices.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AssetStorageTest {

    @Test
    void store_isContentAddressed_sameBytesSameId(@TempDir Path tmp) {
        AssetStorage storage = new AssetStorage(tmp.toString());
        byte[] img = "PNGDATA".getBytes();

        String id1 = storage.store(img);
        String id2 = storage.store(img);

        assertThat(id1).isEqualTo(id2);
        Optional<byte[]> loaded = storage.load(id1);
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(img);
    }

    @Test
    void load_missing_returnsEmpty(@TempDir Path tmp) {
        AssetStorage storage = new AssetStorage(tmp.toString());
        assertThat(storage.load("deadbeef")).isEmpty();
    }
}
