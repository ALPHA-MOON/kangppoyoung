package com.policyfund.notices.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 본문 블록(OpenAPI ContentBlock). type 판별자로 text/image 를 구분한다.
 * 직렬화 시 {"type":"text",...} / {"type":"image",...} 형태가 되어 계약과 일치한다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
        @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image")
})
public sealed interface ContentBlock permits ContentBlock.TextBlock, ContentBlock.ImageBlock {

    record TextBlock(String text) implements ContentBlock {}

    record ImageBlock(String src, String name) implements ContentBlock {}
}
