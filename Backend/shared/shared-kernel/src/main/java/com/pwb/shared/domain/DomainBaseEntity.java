package com.pwb.shared.domain;

import java.time.Instant;

public abstract class DomainBaseEntity {

    private Instant createdAt;
    private Instant updatedAt;

    protected DomainBaseEntity() {
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    protected void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    protected void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    protected void touch() {
        this.updatedAt = Instant.now();
    }
}
