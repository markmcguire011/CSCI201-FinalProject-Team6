package edu.usc.marketplace.model;

import java.time.LocalDateTime;

/** Fields every table has: its id and when the row was created. Every entity class extends it. */
public abstract class BaseEntity {
    protected int id;
    protected LocalDateTime createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
