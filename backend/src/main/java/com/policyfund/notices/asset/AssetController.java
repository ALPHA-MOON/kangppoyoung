package com.policyfund.notices.asset;

import com.policyfund.common.error.ResourceNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices/assets")
public class AssetController {

    private final AssetStorage storage;

    public AssetController(AssetStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> asset(@PathVariable String id) {
        return storage.load(id)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
                .orElseThrow(() -> new ResourceNotFoundException("ASSET_NOT_FOUND",
                        "자산을 찾을 수 없습니다: " + id));
    }
}
