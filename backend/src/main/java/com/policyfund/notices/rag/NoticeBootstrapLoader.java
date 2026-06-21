package com.policyfund.notices.rag;

import com.policyfund.common.error.ResourceNotFoundException;
import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.notices.dto.NoticeRevisionRequest;
import com.policyfund.notices.preprocess.PdfPreprocessService;
import com.policyfund.notices.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 최초 부팅 시 원본 공고 PDF 를 각 카테고리의 v1 으로 시드한다(카테고리에 버전이 없을 때만).
 * 실제 등록 경로(preprocess → registerRevision)를 그대로 타므로 v1 도 동일하게 검색 재색인까지 이어진다.
 * DevDataLoader(@Order 1, out/ 콜드스타트 적재) 이후 실행되어 검색 부트스트랩과 충돌하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "notices.bootstrap.enabled", havingValue = "true")
@Order(2)
public class NoticeBootstrapLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NoticeBootstrapLoader.class);

    private final NoticeService noticeService;
    private final PdfPreprocessService preprocessService;
    private final NoticeSourceStorage sourceStorage;
    private final NoticeBootstrapProperties props;

    public NoticeBootstrapLoader(NoticeService noticeService, PdfPreprocessService preprocessService,
                                 NoticeSourceStorage sourceStorage, NoticeBootstrapProperties props) {
        this.noticeService = noticeService;
        this.preprocessService = preprocessService;
        this.sourceStorage = sourceStorage;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        for (Map.Entry<String, String> entry : props.getSources().entrySet()) {
            String category = entry.getKey();
            try {
                bootstrapCategory(category, Path.of(entry.getValue()));
            } catch (Exception ex) {
                log.error("공고 부트스트랩 실패 — category={} (건너뜀)", category, ex);
            }
        }
    }

    private void bootstrapCategory(String category, Path pdfPath) throws Exception {
        try {
            if (!noticeService.getNotice(category).versions().isEmpty()) {
                log.info("공고 부트스트랩 건너뜀 — category={} 이미 버전 존재", category);
                return;
            }
        } catch (ResourceNotFoundException nf) {
            log.warn("공고 부트스트랩 건너뜀 — category={} 미시드(notice_category 없음)", category);
            return;
        }
        if (!Files.isRegularFile(pdfPath)) {
            log.warn("공고 부트스트랩 건너뜀 — 원본 PDF 없음: {} (source/ 마운트·경로 확인)", pdfPath.toAbsolutePath());
            return;
        }
        byte[] bytes = Files.readAllBytes(pdfPath);
        List<ContentBlock> blocks = preprocessService.preprocess(bytes, "application/pdf");
        String sourceRef = sourceStorage.store(bytes);
        noticeService.registerRevision(category,
                new NoticeRevisionRequest(props.getEffectiveDate(), blocks, sourceRef));
        log.info("공고 부트스트랩 완료 — category={} v1 시드 + 재색인 큐잉 (blocks={})", category, blocks.size());
    }
}
