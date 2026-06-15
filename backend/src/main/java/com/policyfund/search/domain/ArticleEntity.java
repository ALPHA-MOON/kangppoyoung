package com.policyfund.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "article")
public class ArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id")
    private String docId;

    @Column(name = "doc_title")
    private String docTitle;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "article_no")
    private String articleNo;

    @Column(name = "`text`")
    private String text;

    protected ArticleEntity() {}

    public ArticleEntity(String docId, String docTitle, String docType, String articleNo, String text) {
        this.docId = docId;
        this.docTitle = docTitle;
        this.docType = docType;
        this.articleNo = articleNo;
        this.text = text;
    }

    public Long getId() { return id; }
    public String getDocId() { return docId; }
    public String getDocTitle() { return docTitle; }
    public String getDocType() { return docType; }
    public String getArticleNo() { return articleNo; }
    public String getText() { return text; }
}
