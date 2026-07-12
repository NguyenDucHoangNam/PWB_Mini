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
@Table(name = "demo_distributions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class DemoDistribution extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "demo_id", nullable = false, updatable = false)
    private UUID demoId;

    @Column(name = "recipient_email", nullable = false, length = 100, updatable = false)
    private String recipientEmail;

    @Column(name = "share_token", nullable = false, unique = true, updatable = false)
    private UUID shareToken;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    public void revoke(Instant when) {
        if (!this.revoked) {
            this.revoked = true;
            this.revokedAt = when;
        }
    }

    public void incrementPlayCount(Instant when) {
        this.playCount += 1;
        this.lastPlayedAt = when;
    }
}