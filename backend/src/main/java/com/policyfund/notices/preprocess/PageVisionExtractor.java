package com.policyfund.notices.preprocess;

/** 이미지 전용 PDF 페이지(PNG 바이트)에서 텍스트·표·도표를 인식해 텍스트로 반환한다. */
public interface PageVisionExtractor {
    String extractText(byte[] pageImagePng);
}
