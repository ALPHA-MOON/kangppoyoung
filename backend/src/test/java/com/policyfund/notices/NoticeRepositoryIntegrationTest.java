package com.policyfund.notices;

import com.policyfund.notices.domain.NoticeVersionEntity;
import com.policyfund.notices.domain.NoticeVersionRepository;
import com.policyfund.notices.dto.ContentBlock;
import com.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    NoticeVersionRepository versions;

    @Test
    void savesBlocksAsJsonAndQueriesByDateDesc() {
        versions.save(new NoticeVersionEntity("regulation", "v1", LocalDate.parse("2026-01-01"),
                List.of(new ContentBlock.TextBlock("v1 본문"))));
        versions.save(new NoticeVersionEntity("regulation", "v2", LocalDate.parse("2026-02-01"),
                List.of(new ContentBlock.TextBlock("v2 본문"),
                        new ContentBlock.ImageBlock("/api/v1/notices/assets/x", "그림.png"))));

        List<NoticeVersionEntity> result =
                versions.findByCategoryKeyOrderByDateDescVersionDesc("regulation");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersion()).isEqualTo("v2");
        assertThat(result.get(0).getBlocks()).hasSize(2);
        assertThat(result.get(0).getBlocks().get(1)).isInstanceOf(ContentBlock.ImageBlock.class);
    }
}
