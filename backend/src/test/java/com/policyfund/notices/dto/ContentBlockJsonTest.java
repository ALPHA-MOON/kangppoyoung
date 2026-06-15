package com.policyfund.notices.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBlockJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void textBlock_serializesWithTypeDiscriminator() throws Exception {
        String json = mapper.writeValueAsString(new ContentBlock.TextBlock("제5조 내용"));
        assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"제5조 내용\"");
    }

    @Test
    void imageBlock_roundTrips() throws Exception {
        ContentBlock original = new ContentBlock.ImageBlock("/api/v1/notices/assets/abc", "표1.png");
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("\"type\":\"image\"");
        ContentBlock back = mapper.readValue(json, ContentBlock.class);
        assertThat(back).isEqualTo(original);
    }

    @Test
    void list_deserializesMixedBlocks() throws Exception {
        String json = "[{\"type\":\"text\",\"text\":\"a\"},{\"type\":\"image\",\"src\":\"s\",\"name\":\"n\"}]";
        List<ContentBlock> blocks = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, ContentBlock.class));
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(blocks.get(1)).isInstanceOf(ContentBlock.ImageBlock.class);
    }
}
