package com.pwb.voice.infrastructure.persistence.entity;

import com.pwb.voice.api.enums.VoiceTagType;
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
    name = "voice_voice_tags",
    indexes = {
        @Index(name = "ix_voice_tags_user_id", columnList = "user_id"),
        @Index(name = "ix_voice_tags_tag_type", columnList = "tag_type")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_voice_tags_user_name", columnNames = {"user_id", "name"})
    }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceTagJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 32)
    private VoiceTagType tagType;

    @Column(name = "source_text", length = 4000)
    private String sourceText;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
