package com.pwb.audio.infrastructure.persistence.entity;

import com.pwb.audio.domain.enums.VoiceTagType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "audio_voice_tags",
        indexes = {
                @Index(name = "ix_audio_voice_tags_tag_type", columnList = "tag_type"),
                @Index(name = "ix_audio_voice_tags_user_created_at", columnList = "user_id, created_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_audio_voice_tags_user_name", columnNames = {"user_id", "name"})
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceTagJpaEntity extends AudioJpaBaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 16)
    private VoiceTagType tagType;

    @Column(name = "source_text", length = 2048)
    private String sourceText;

    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(name = "voice_name", length = 64)
    private String voiceName;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
