package com.pwb.backend.audio.internal.model;

import com.pwb.backend.audio.internal.enums.DemoStatus;
import com.pwb.backend.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "demos")
public class Demo extends BaseEntity {

  @Column(name = "owner_id", nullable = false, length = 36)
  private String ownerId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(name = "original_s3_key", nullable = false, length = 255)
  private String originalS3Key;

  @Column(name = "confirmed_s3_key", length = 255)
  private String confirmedS3Key;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "voice_tag_id", length = 36)
  private String voiceTagId;

  @Column(name = "watermark_interval", nullable = false)
  private int watermarkInterval = 25;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DemoStatus status = DemoStatus.PROCESSING;

  @Column(name = "hls_playlist_s3_key", length = 255)
  private String hlsPlaylistS3Key;

  @Column(precision = 10, scale = 2)
  private BigDecimal duration;

  @Column(name = "sample_rate")
  private Integer sampleRate;

  @Column(length = 10)
  private String format;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "waveform_data", columnDefinition = "jsonb")
  private List<Float> waveformData;

  @Column(name = "aes_key_encrypted")
  private byte[] aesKeyEncrypted;

  @Column(name = "aes_key_version")
  private Integer aesKeyVersion;

  @Column(name = "previous_aes_key_encrypted")
  private byte[] previousAesKeyEncrypted;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "stream_s3_cleanup_at")
  private Instant streamS3CleanupAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
