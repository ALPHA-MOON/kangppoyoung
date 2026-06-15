package com.policyfund.notices.preprocess;

import com.policyfund.common.error.BadRequestException;
import com.policyfund.notices.asset.AssetStorage;
import com.policyfund.notices.dto.ContentBlock;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 업로드 PDF → 검토용 ContentBlock[].
 * 페이지별로 텍스트 레이어가 있으면 로컬 추출(TextBlock), 없으면(이미지 전용) 페이지를
 * PNG 로 렌더링해 자산으로 저장(ImageBlock)하고 Vision 으로 텍스트를 인식(TextBlock)한다.
 */
@Service
public class PdfPreprocessService {

    private final AssetStorage assets;
    private final PageVisionExtractor vision;
    private final long maxBytes;

    public PdfPreprocessService(AssetStorage assets,
                                PageVisionExtractor vision,
                                @Value("${app.preprocess.max-bytes:52428800}") long maxBytes) {
        this.assets = assets;
        this.vision = vision;
        this.maxBytes = maxBytes;
    }

    public List<ContentBlock> preprocess(byte[] pdf, String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith("application/pdf")) {
            throw new BadRequestException("INVALID_FILE_TYPE", "PDF 파일만 업로드할 수 있습니다.");
        }
        if (pdf.length == 0) {
            throw new BadRequestException("EMPTY_FILE", "빈 파일입니다.");
        }
        if (pdf.length > maxBytes) {
            throw new BadRequestException("FILE_TOO_LARGE", "파일이 너무 큽니다.");
        }

        List<ContentBlock> blocks = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(doc).trim();
                if (!text.isEmpty()) {
                    blocks.add(new ContentBlock.TextBlock(text));
                } else {
                    byte[] png = renderPagePng(renderer, i);
                    String id = assets.store(png);
                    blocks.add(new ContentBlock.ImageBlock("/api/v1/notices/assets/" + id, "page-" + (i + 1) + ".png"));
                    String recognized = vision.extractText(png);
                    if (recognized != null && !recognized.isBlank()) {
                        blocks.add(new ContentBlock.TextBlock(recognized.trim()));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return blocks;
    }

    private static byte[] renderPagePng(PDFRenderer renderer, int pageIndex) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
