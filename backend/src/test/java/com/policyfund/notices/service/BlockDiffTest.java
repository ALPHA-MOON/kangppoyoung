package com.policyfund.notices.service;

import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.notices.dto.DiffBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDiffTest {

    @Test
    void detectsSameAddDel() {
        List<ContentBlock> oldB = List.of(
                new ContentBlock.TextBlock("A"),
                new ContentBlock.TextBlock("B"));
        List<ContentBlock> newB = List.of(
                new ContentBlock.TextBlock("A"),
                new ContentBlock.TextBlock("C"));

        List<DiffBlock> diff = BlockDiff.diff(oldB, newB);

        assertThat(diff).extracting(DiffBlock::type)
                .containsExactly("same", "del", "add");
        assertThat(((ContentBlock.TextBlock) diff.get(0).block()).text()).isEqualTo("A");
        assertThat(((ContentBlock.TextBlock) diff.get(1).block()).text()).isEqualTo("B");
        assertThat(((ContentBlock.TextBlock) diff.get(2).block()).text()).isEqualTo("C");
    }

    @Test
    void noPrevious_allAdded() {
        List<DiffBlock> diff = BlockDiff.diff(List.of(),
                List.of(new ContentBlock.TextBlock("X")));
        assertThat(diff).extracting(DiffBlock::type).containsExactly("add");
    }
}
