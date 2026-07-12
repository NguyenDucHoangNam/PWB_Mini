package com.pwb.backend.modules.share.entity;

import com.pwb.backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_threads")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class SharedThread extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "producer_id", nullable = false, updatable = false)
    private UUID producerId;

    @Column(name = "recipient_email", nullable = false, length = 100, updatable = false)
    private String recipientEmail;

    @Column(name = "recipient_email_hash", nullable = false, length = 64, updatable = false)
    private String recipientEmailHash;

    @Column(name = "last_interacted_at", nullable = false)
    private Instant lastInteractedAt;

    public void touchLastInteracted(Instant when) {
        this.lastInteractedAt = when;
    }
}