package com.pwb.voice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "voice_song_tag_configs",
    indexes = {
        @Index(name = "ix_song_tag_configs_song_id", columnList = "song_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_song_tag_config_song", columnNames = {"song_id"})
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongTagConfigJpaEntity extends BaseEntity {

    @Column(name = "song_id", nullable = false)
    private UUID songId;

    @Column(name = "voice_tag_id", nullable = false)
    private UUID voiceTagId;

    @Column(name = "interval_seconds", nullable = false)
    private Integer intervalSeconds;

    @Column(name = "volume_percentage", nullable = false)
    private Integer volumePercentage;

    @Column(name = "fade_in_duration_ms", nullable = false)
    private Integer fadeInDurationMs;

    @Column(name = "fade_out_duration_ms", nullable = false)
    private Integer fadeOutDurationMs;

    @Column(name = "start_offset_seconds", nullable = false)
    private Integer startOffsetSeconds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
