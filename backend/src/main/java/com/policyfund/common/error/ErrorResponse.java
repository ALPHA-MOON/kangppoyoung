package com.policyfund.common.error;

/** OpenAPI Error 스키마(code, message)와 1:1. */
public record ErrorResponse(String code, String message) {}
