package com.policyfund.notices.web;

import jakarta.validation.Valid;
import com.policyfund.notices.dto.DiffBlock;
import com.policyfund.notices.dto.NoticeCategoryDto;
import com.policyfund.notices.dto.NoticeRevisionRequest;
import com.policyfund.notices.dto.NoticeVersionDto;
import com.policyfund.notices.preprocess.PdfPreprocessService;
import com.policyfund.notices.preprocess.PreprocessResponse;
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

    public NoticeController(NoticeService service, PdfPreprocessService preprocessService) {
        this.service = service;
        this.preprocessService = preprocessService;
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
            return new PreprocessResponse(
                    preprocessService.preprocess(file.getBytes(), file.getContentType()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
