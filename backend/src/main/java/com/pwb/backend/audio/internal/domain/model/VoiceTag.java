package com.pwb.backend.audio.internal.domain.model;

import com.pwb.backend.shared.kernel.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "voice_tags",
    indexes = {
        @Index(name = "idx_voice_tags_owner_active", columnList = "owner_id")
    }
)
public class VoiceTag extends BaseEntity {

  @Column(name = "owner_id", nullable = false, length = 36)
  private String ownerId;

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

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "is_deleted", nullable = false)
  private boolean isDeleted;

  @Column(name = "deleted_at")
  private java.time.Instant deletedAt;
}
