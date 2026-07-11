package com.pwb.backend.audio.internal.interfaces.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@ConfigurationProperties(prefix = "app.audio")
@Validated
public class AudioProperties {

  private Upload upload = new Upload();
  private Stream stream = new Stream();
  private Worker worker = new Worker();
  private RateLimit rateLimit = new RateLimit();
  private Aes aes = new Aes();
  private Webhook webhook = new Webhook();
  private VoiceTag voiceTag = new VoiceTag();
  private Tts tts = new Tts();
  private Distribution distribution = new Distribution();
  private Outbox outbox = new Outbox();
  private StreamSecureCookie streamSecureCookie = new StreamSecureCookie();
  private Download download = new Download();
  private Revoke revoke = new Revoke();
  private Otp otp = new Otp();
  private Play play = new Play();
  private IpHash ipHash = new IpHash();

  @Getter
  public static class Upload {
    @Min(1)
    private long maxFileSize = 209715200L;
    @Min(1)
    private int presignedUrlTtl = 60;
    @Min(1)
    private int claimRedisTtl = 7200;
    @Min(0)
    private int perUserActiveQuota = 20;
    @Min(0)
    private long perUserStorageQuota = 5368709120L;
  }

  @Getter
  public static class Stream {
    @Valid
    private Hls hls = new Hls();

    @Getter
    public static class Hls {
      @Min(1)
      private int segmentDurationSeconds = 6;
      @Min(16)
      private int aesKeyBytes = 16;
    }
  }

  @Getter
  public static class Worker {
    @NotBlank
    private String ffmpegPath = "ffmpeg";
    @NotBlank
    private String ffprobePath = "ffprobe";
    @Min(1)
    private int concurrencyCap = 2;
    @Min(1)
    private int processingTimeoutSeconds = 900;
    @Min(0)
    private int maxRetryAttempts = 3;
    @NotBlank
    private String tempDir = "/tmp/pwb-audio";
  }

  @Getter
  public static class RateLimit {
    private Bucket presignedUpload = new Bucket(5, 60, 10, 3600);
    private Bucket confirmUpload = new Bucket(30, 60, 30, 60);
    private Bucket voiceTagCreate = new Bucket(2, 60, 30, 86400);
    private Bucket voiceTagPreview = new Bucket(60, 60, 10, 60);
    private Bucket voiceTagDefault = new Bucket(10, 60, 10, 60);
    private Bucket voiceTagList = new Bucket(60, 60, 60, 60);
    private Bucket voiceTagDelete = new Bucket(10, 60, 5, 60);
    private Bucket distributionCreate = new Bucket(10, 60, 30, 60);
    private Bucket recipientSuggest = new Bucket(30, 60, 60, 60);
    private Bucket sharedGet = new Bucket(60, 60, 120, 60);
    private Bucket sharedKeys = new Bucket(60, 60, 600, 60);
    private Bucket sharedTrackPlay = new Bucket(5, 60, 10, 60);
    private Bucket sharedRequestOtp = new Bucket(5, 60, 5, 600);
    private Bucket sharedVerifyOtp = new Bucket(10, 60, 10, 600);
    private Bucket sharedDownload = new Bucket(5, 60, 10, 60);
    private Bucket sharedWsToken = new Bucket(10, 60, 30, 60);
    private Bucket revoke = new Bucket(20, 60, 30, 60);

    @Getter
    public static class Bucket {
      @Min(0)
      private int ipMax;
      @Min(1)
      private int ipWindowSeconds;
      @Min(0)
      private int userMax;
      @Min(1)
      private int userWindowSeconds;

      public Bucket() {}

      public Bucket(int ipMax, int ipWindowSeconds, int userMax, int userWindowSeconds) {
        this.ipMax = ipMax;
        this.ipWindowSeconds = ipWindowSeconds;
        this.userMax = userMax;
        this.userWindowSeconds = userWindowSeconds;
      }
    }
  }

  @Getter
  public static class Aes {
    @NotBlank
    @Pattern(regexp = ".{32,}",
        message = "masterKey must be at least 32 chars (AES-256 requires 256-bit key)")
    private String masterKey = "";
    @Min(1)
    private int keyVersion = 1;
    @NotBlank
    @Pattern(regexp = ".{32,}",
        message = "outboxEncryptionKey must be at least 32 chars")
    private String outboxEncryptionKey = "";
  }

  @Getter
  public static class Webhook {
    @NotBlank
    private String demoStatusQueue = "/user/queue/demos/status";
  }

  @Getter
  public static class VoiceTag {
    @Min(1)
    private int rawTextMaxLength = 100;
    @Min(1)
    private int ssmlMaxLength = 250;
    @Min(1)
    private int ssmlMaxDepth = 3;
    @Min(0)
    private int maxActive = 50;
    @Min(0)
    private int maxStorageBytes = 209715200;
    @Min(1)
    private int presignedTtlSeconds = 30;
    @Min(0)
    private int dailyQuota = 30;
    @Min(0)
    private int restoreWindowDays = 7;
  }

  @Getter
  public static class Tts {
    @NotBlank
    private String credentialsPath = "";
    @NotBlank
    private String projectId = "";
    @NotBlank
    @Pattern(regexp = "[a-z]{2}-[A-Z]{2}",
        message = "defaultLanguageCode must follow BCP-47 (e.g. vi-VN)")
    private String defaultLanguageCode = "vi-VN";
    @Min(1)
    private int responseMinBytes = 1000;
    @Min(1)
    private int responseMaxBytes = 5242880;
    @Min(1)
    private int voicesCacheTtlSeconds = 86400;
    @Min(1)
    private int httpTimeoutSeconds = 30;
  }

  @Getter
  public static class Distribution {
    @Min(0)
    private int dailyRecipientsQuota = 100;
    @Min(0)
    private int dailyCountQuota = 500;
    @Min(0)
    private int autocompleteMaxResults = 10;
    @Min(1)
    private int autocompleteQueryMinLength = 2;
    @Min(1)
    private int autocompleteQueryMaxLength = 50;
    @Min(1)
    private int shareLinkBaseUrlTtlSeconds = 86400;
    @Min(1)
    private int blacklistDomainCacheTtlSeconds = 3600;
    @Min(1)
    private int idorWarnThreshold = 5;
    @Min(1)
    private int idorWarnWindowSeconds = 600;
    @Min(1)
    private int idorBlockSeconds = 3600;
    @NotBlank
    private String shareLinkBaseUrl = "https://pwbmini.com";
  }

  @Getter
  public static class Outbox {
    @Min(1)
    private int deadLetterAfterRetries = 5;
    @Min(0)
    private int initialBackoffSeconds = 1;
    @Min(1)
    private int maxBackoffSeconds = 60;
  }

  @Getter
  public static class StreamSecureCookie {
    @NotBlank
    @Pattern(regexp = ".{32,}",
        message = "secret must be at least 32 chars for HMAC-SHA256")
    private String secret = "";
    @Min(1)
    private int ttlSeconds = 1800;
    @NotBlank
    private String cookieName = "__Host-pwb_stream_sess";
    private boolean cookieSecure = true;
    @NotBlank
    @Pattern(regexp = "Lax|Strict|None",
        message = "cookieSameSite must be one of: Lax, Strict, None")
    private String cookieSameSite = "Strict";
    @Min(0)
    private int cidrMaskBitsV4 = 24;
    @Min(0)
    private int cidrMaskBitsV6 = 48;
    @Min(1)
    private int secretRotationDays = 30;
  }

  @Getter
  public static class Download {
    @Min(1)
    private int presignedUrlTtlSeconds = 300;
    private boolean requireSecureCookie = true;
    @Min(1)
    private int filenameFallbackMaxLength = 100;
    @NotBlank
    private String filenameSanitizeRegex = "[^a-zA-Z0-9._-]";
    @NotBlank
    private String filenameBlocklistChars = "[\r\n\t;\"']";
    @NotBlank
    private String xContentTypeOptions = "nosniff";
    @NotBlank
    private String cacheControl = "private, no-store, max-age=0";
    @Min(0)
    private int perSessionDailyQuota = 10;
    @Min(0)
    private int perShareTokenWeeklyQuota = 100;
    @Min(1)
    private long maxFileBytes = 209715200L;
    @Min(0)
    private int auditRetentionDays = 90;
  }

  @Getter
  public static class Revoke {
    @Min(1)
    private int blacklistTtlSeconds = 600;
    @Min(1)
    private int idempotencyTtlSeconds = 86400;
    @Min(0)
    private int perUserRateLimit = 30;
    @Min(0)
    private int perIpRateLimit = 20;
    private boolean notifyListenerWebsocket = true;
    @NotBlank
    private String distributionRevokedQueue = "/user/queue/distributions/revoked";
  }

  @Getter
  public static class Otp {
    @Min(4)
    @Pattern(regexp = "[0-9]+",
        message = "length must be numeric")
    private int length = 6;
    @Min(0)
    private int cooldownSeconds = 60;
    @Min(1)
    private int maxAttempts = 5;
    @Min(0)
    private int lockSeconds = 900;
    @Min(1)
    private int codeTtlSeconds = 300;
  }

  @Getter
  public static class Play {
    @Min(1)
    private int antiFraudWindowSeconds = 30;
    @Min(1)
    private int maxPlaysPerSession = 50;
    @Min(0)
    private int minPlaySecondsAfter30pct = 15;
    @Min(1)
    private int keysRequestCountMax = 600;
    @NotBlank
    @Pattern(regexp = "SHA-(256|512)",
        message = "fingerprintAlgorithm must be SHA-256 or SHA-512")
    private String fingerprintAlgorithm = "SHA-256";
  }

  @Getter
  public static class IpHash {
    @NotBlank
    @Min(8)
    private String salt = "pwb-mini-ip-hash-salt-do-not-use-in-prod";
  }
}