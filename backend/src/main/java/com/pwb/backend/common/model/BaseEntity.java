package com.pwb.backend.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public abstract class BaseEntity {

    public static final String SYSTEM_PRINCIPAL = "SYSTEM";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    @Setter(AccessLevel.NONE)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    @Setter(AccessLevel.NONE)
    private String updatedBy;

    @Column(name = "deleted_at")
    @Setter(AccessLevel.NONE)
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 64)
    @Setter(AccessLevel.NONE)
    private String deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private long version;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(String actor) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actor == null || actor.isBlank() ? SYSTEM_PRINCIPAL : actor;
        }
    }

    public void restore() {
        if (deletedAt != null) {
            this.deletedAt = null;
            this.deletedBy = null;
        }
    }
}
