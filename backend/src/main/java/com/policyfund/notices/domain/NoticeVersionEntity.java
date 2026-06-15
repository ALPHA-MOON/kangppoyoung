package com.policyfund.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.policyfund.notices.dto.ContentBlock;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "notice_version")
public class NoticeVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_key")
    private String categoryKey;

    private String version;

    @Column(name = "`date`")
    private LocalDate date;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocks_json", columnDefinition = "json")
    private List<ContentBlock> blocks;

    protected NoticeVersionEntity() {}

    public NoticeVersionEntity(String categoryKey, String version, LocalDate date, List<ContentBlock> blocks) {
        this.categoryKey = categoryKey;
        this.version = version;
        this.date = date;
        this.blocks = blocks;
    }

    public Long getId() { return id; }
    public String getCategoryKey() { return categoryKey; }
    public String getVersion() { return version; }
    public LocalDate getDate() { return date; }
    public List<ContentBlock> getBlocks() { return blocks; }
}
