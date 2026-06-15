package com.policyfund.notices.preprocess;

import com.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PreprocessApiIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class MockVisionConfig {
        @Bean @Primary
        PageVisionExtractor mockVision() {
            return png -> "VISION TEXT";
        }
    }

    @Autowired
    MockMvc mvc;

    private byte[] minimalTextPdf() throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Notice body");
                cs.endText();
            }
            var out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void preprocess_pdf_returnsBlocks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "rev.pdf", "application/pdf", minimalTextPdf());

        mvc.perform(multipart("/api/v1/notices/regulation/revisions/preprocess").file(file))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.blocks").isArray())
           .andExpect(jsonPath("$.blocks[0].type").value("text"))
           .andExpect(jsonPath("$.blocks[0].text").value(org.hamcrest.Matchers.containsString("Notice body")));
    }

    @Test
    void preprocess_nonPdf_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", "notpdf".getBytes());

        mvc.perform(multipart("/api/v1/notices/regulation/revisions/preprocess").file(file))
           .andExpect(status().isBadRequest());
    }
}
