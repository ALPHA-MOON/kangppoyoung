package com.policyfund;

import com.policyfund.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationCreatesAllCoreTables() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_name IN " +
                "('policy_document','article','search_history','search_example'," +
                "'notice_category','notice_version','ranking_cache')",
                Integer.class, MYSQL.getDatabaseName());
        assertThat(count).isEqualTo(7);
    }

    @Test
    void articleHasNgramFulltextIndex() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = ? AND table_name = 'article' " +
                "AND index_name = 'ft_article_text'",
                Integer.class, MYSQL.getDatabaseName());
        assertThat(count).isGreaterThan(0);
    }
}
