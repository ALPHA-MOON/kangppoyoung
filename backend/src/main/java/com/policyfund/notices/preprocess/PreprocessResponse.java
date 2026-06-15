package com.policyfund.notices.preprocess;

import com.policyfund.notices.dto.ContentBlock;

import java.util.List;

public record PreprocessResponse(List<ContentBlock> blocks) {}
