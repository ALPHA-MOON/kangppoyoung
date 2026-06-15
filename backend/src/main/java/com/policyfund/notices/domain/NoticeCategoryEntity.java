package com.policyfund.notices.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notice_category")
public class NoticeCategoryEntity {

    @Id
    @Column(name = "`key`")
    private String key;

    private String label;

    @Column(name = "doc_title")
    private String docTitle;

    protected NoticeCategoryEntity() {}

    public NoticeCategoryEntity(String key, String label, String docTitle) {
        this.key = key;
        this.label = label;
        this.docTitle = docTitle;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getDocTitle() { return docTitle; }
}
