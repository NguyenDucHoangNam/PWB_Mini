package com.pwb.backend.entity.rdbms;

import com.pwb.backend.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "voice_tags")
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceTag extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "text_content", nullable = false, length = 500)
    private String textContent;

    @Column(name = "voice_name", nullable = false, length = 100)
    private String voiceName;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "s3_key", nullable = false, length = 255)
    private String s3Key;

    @Column(name = "file_size", nullable = false)
    private int fileSize;

    @Column(name = "storage_url", length = 255)
    private String storageUrl;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
