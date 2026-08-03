package com.pwb.audio.infrastructure.persistence.entity;

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
        name = "audio_song_tag_configs",
        indexes = {
                @Index(name = "ix_audio_song_tag_configs_song_id", columnList = "song_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_audio_song_tag_configs_song_id", columnNames = "song_id")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongTagConfigJpaEntity extends AudioJpaBaseEntity {

    @Column(name = "song_id", nullable = false)
    private UUID songId;

    @Column(name = "voice_tag_id", nullable = false)
    private UUID voiceTagId;

    @Column(name = "interval_seconds")
    private Integer intervalSeconds;

    @Column(name = "volume_percentage")
    private Integer volumePercentage;

    @Column(name = "ducking_percentage", nullable = false)
    private Integer duckingPercentage;

    @Column(name = "start_offset_seconds")
    private Integer startOffsetSeconds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
