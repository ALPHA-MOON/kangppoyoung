package com.policyfund.notices.preprocess;

import com.policyfund.common.error.BadRequestException;
import com.policyfund.notices.asset.AssetStorage;
import com.policyfund.notices.dto.ContentBlock;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfPreprocessServiceTest {

    private PdfPreprocessService service(Path tmp, PageVisionExtractor vision) {
        return new PdfPreprocessService(new AssetStorage(tmp.toString()), vision, 50L * 1024 * 1024);
    }

    private byte[] textPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void textLayerPage_yieldsTextBlock_withoutCallingVision(@TempDir Path tmp) throws Exception {
        PageVisionExtractor vision = mock(PageVisionExtractor.class);
        PdfPreprocessService service = service(tmp, vision);

        List<ContentBlock> blocks = service.preprocess(textPdf("Hello Policy"), "application/pdf");

        assertThat(blocks).isNotEmpty();
        assertThat(blocks.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) blocks.get(0)).text()).contains("Hello Policy");
        verifyNoInteractions(vision);
    }

    @Test
    void rejectsNonPdfContentType(@TempDir Path tmp) {
        PdfPreprocessService service = service(tmp, mock(PageVisionExtractor.class));
        assertThatThrownBy(() -> service.preprocess("x".getBytes(), "image/png"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOversizeFile(@TempDir Path tmp) throws Exception {
        PdfPreprocessService small = new PdfPreprocessService(
                new AssetStorage(tmp.toString()), mock(PageVisionExtractor.class), 10L);
        assertThatThrownBy(() -> small.preprocess(textPdf("too big"), "application/pdf"))
                .isInstanceOf(BadRequestException.class);
    }
}
