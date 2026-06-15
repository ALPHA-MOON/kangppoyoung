package com.policyfund.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "search_example")
public class SearchExampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Byte slot;

    @Column(name = "`text`")
    private String text;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SearchExampleEntity() {}

    public SearchExampleEntity(Byte slot, String text, Instant createdAt) {
        this.slot = slot;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Byte getSlot() { return slot; }
    public String getText() { return text; }
}
