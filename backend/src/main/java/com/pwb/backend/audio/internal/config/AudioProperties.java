package com.pwb.backend.audio.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audio")
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
  @Setter
  public static class Upload {
    private long maxFileSize = 209715200L;
    private int presignedUrlTtl = 60;
    private int claimRedisTtl = 7200;
    private int perUserActiveQuota = 20;
    private long perUserStorageQuota = 5368709120L;
  }

  @Getter
  @Setter
  public static class Stream {
    private Hls hls = new Hls();

    @Getter
    @Setter
    public static class Hls {
      private int segmentDurationSeconds = 6;
      private int aesKeyBytes = 16;
    }
  }

  @Getter
  @Setter
  public static class Worker {
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private int concurrencyCap = 2;
    private int processingTimeoutSeconds = 900;
    private int maxRetryAttempts = 3;
    private String tempDir = "/tmp/pwb-audio";
  }

  @Getter
  @Setter
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
    @Setter
    public static class Bucket {
      private int ipMax;
      private int ipWindowSeconds;
      private int userMax;
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
  @Setter
  public static class Aes {
    private String masterKey = "";
    private int keyVersion = 1;
    private String outboxEncryptionKey = "";
  }

  @Getter
  @Setter
  public static class Webhook {
    private String demoStatusQueue = "/user/queue/demos/status";
  }

  @Getter
  @Setter
  public static class VoiceTag {
    private int rawTextMaxLength = 100;
    private int ssmlMaxLength = 250;
    private int ssmlMaxDepth = 3;
    private int maxActive = 50;
    private int maxStorageBytes = 209715200;
    private int presignedTtlSeconds = 30;
    private int dailyQuota = 30;
    private int restoreWindowDays = 7;
  }

  @Getter
  @Setter
  public static class Tts {
    private String credentialsPath = "";
    private String projectId = "";
    private String defaultLanguageCode = "vi-VN";
    private int responseMinBytes = 1000;
    private int responseMaxBytes = 5242880;
    private int voicesCacheTtlSeconds = 86400;
    private int httpTimeoutSeconds = 30;
  }

  @Getter
  @Setter
  public static class Distribution {
    private int dailyRecipientsQuota = 100;
    private int dailyCountQuota = 500;
    private int autocompleteMaxResults = 10;
    private int autocompleteQueryMinLength = 2;
    private int autocompleteQueryMaxLength = 50;
    private int shareLinkBaseUrlTtlSeconds = 86400;
    private int blacklistDomainCacheTtlSeconds = 3600;
    private int idorWarnThreshold = 5;
    private int idorWarnWindowSeconds = 600;
    private int idorBlockSeconds = 3600;
    private String shareLinkBaseUrl = "https://pwbmini.com";
  }

  @Getter
  @Setter
  public static class Outbox {
    private int deadLetterAfterRetries = 5;
    private int initialBackoffSeconds = 1;
    private int maxBackoffSeconds = 60;
    private int kafkaTopic = 1;
  }

  @Getter
  @Setter
  public static class StreamSecureCookie {
    private String secret = "";
    private int ttlSeconds = 1800;
    private String cookieName = "__Host-pwb_stream_sess";
    private boolean cookieSecure = true;
    private String cookieSameSite = "Strict";
    private int cidrMaskBitsV4 = 24;
    private int cidrMaskBitsV6 = 48;
    private int secretRotationDays = 30;
  }

  @Getter
  @Setter
  public static class Download {
    private int presignedUrlTtlSeconds = 300;
    private boolean requireSecureCookie = true;
    private int filenameFallbackMaxLength = 100;
    private String filenameSanitizeRegex = "[^a-zA-Z0-9._-]";
    private String filenameBlocklistChars = "[\r\n\t;\"']";
    private String xContentTypeOptions = "nosniff";
    private String cacheControl = "private, no-store, max-age=0";
    private int perSessionDailyQuota = 10;
    private int perShareTokenWeeklyQuota = 100;
    private long maxFileBytes = 209715200L;
    private int auditRetentionDays = 90;
  }

  @Getter
  @Setter
  public static class Revoke {
    private int blacklistTtlSeconds = 600;
    private int idempotencyTtlSeconds = 86400;
    private int perUserRateLimit = 30;
    private int perIpRateLimit = 20;
    private boolean notifyListenerWebsocket = true;
    private String distributionRevokedQueue = "/user/queue/distributions/revoked";
  }

  @Getter
  @Setter
  public static class Otp {
    private int length = 6;
    private int cooldownSeconds = 60;
    private int maxAttempts = 5;
    private int lockSeconds = 900;
    private int codeTtlSeconds = 300;
  }

  @Getter
  @Setter
  public static class Play {
    private int antiFraudWindowSeconds = 30;
    private int maxPlaysPerSession = 50;
    private int minPlaySecondsAfter30pct = 15;
    private int keysRequestCountMax = 600;
    private String fingerprintAlgorithm = "SHA-256";
  }

  @Getter
  @Setter
  public static class IpHash {
    private String salt = "pwb-mini-ip-hash-salt-do-not-use-in-prod";
  }
}
