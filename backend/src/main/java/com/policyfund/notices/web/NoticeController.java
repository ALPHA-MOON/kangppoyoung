package com.policyfund.notices.web;

import jakarta.validation.Valid;
import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.notices.dto.DiffBlock;
import com.policyfund.notices.dto.NoticeCategoryDto;
import com.policyfund.notices.dto.NoticeRevisionRequest;
import com.policyfund.notices.dto.NoticeVersionDto;
import com.policyfund.notices.preprocess.PdfPreprocessService;
import com.policyfund.notices.preprocess.PreprocessResponse;
import com.policyfund.notices.rag.NoticeSourceStorage;
import com.policyfund.notices.service.NoticeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService service;
    private final PdfPreprocessService preprocessService;
    private final NoticeSourceStorage sourceStorage;

    public NoticeController(NoticeService service, PdfPreprocessService preprocessService,
                           NoticeSourceStorage sourceStorage) {
        this.service = service;
        this.preprocessService = preprocessService;
        this.sourceStorage = sourceStorage;
    }

    @GetMapping("/{category}")
    public NoticeCategoryDto getNotice(@PathVariable String category) {
        return service.getNotice(category);
    }

    @PostMapping("/{category}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeVersionDto registerRevision(@PathVariable String category,
                                             @Valid @RequestBody NoticeRevisionRequest request) {
        return service.registerRevision(category, request);
    }

    @GetMapping("/{category}/versions/{version}/diff")
    public List<DiffBlock> diff(@PathVariable String category, @PathVariable String version) {
        return service.diff(category, version);
    }

    @PostMapping(value = "/{category}/revisions/preprocess",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreprocessResponse preprocess(@PathVariable String category,
                                         @RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            // 먼저 검증·추출(비PDF면 여기서 400). 통과한 원본만 재색인용으로 보관하고 ref 를 돌려준다.
            List<ContentBlock> blocks = preprocessService.preprocess(bytes, file.getContentType());
            String sourceRef = sourceStorage.store(bytes);
            return new PreprocessResponse(blocks, sourceRef);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
