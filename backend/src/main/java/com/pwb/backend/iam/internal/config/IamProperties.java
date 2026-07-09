package com.pwb.backend.iam.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.iam")
public class IamProperties {

  private Jwt jwt = new Jwt();
  private Otp otp = new Otp();
  private Login login = new Login();
  private Account account = new Account();
  private Outbox outbox = new Outbox();
  private Google google = new Google();
  private GeoIp geoip = new GeoIp();
  private Session session = new Session();

  @Getter
  @Setter
  public static class Session {
    private boolean cookieSecure = true;
    private String cookieSameSite = "Lax";
  }

  @Getter
  @Setter
  public static class Jwt {
    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
  }

  @Getter
  @Setter
  public static class Otp {
    private long expiration;
    private long cooldown;
    private int maxAttempts;
  }

  @Getter
  @Setter
  public static class Login {
    private int maxFailedAttempts;
    private long lockoutTtlSeconds;
    private long passwordResetTokenTtlSeconds;
    private Lockout lockout = new Lockout();

    @Getter
    @Setter
    public static class Lockout {
      private int maxAttempts = 5;
      private long windowDurationMinutes = 15;
    }
  }

  @Getter
  @Setter
  public static class Account {
    private int deletionGraceDays;
  }

  @Getter
  @Setter
  public static class Outbox {
    private int deadLetterAfterRetries;
    private String anonymizationCron;
  }

  @Getter
  @Setter
  public static class Google {
    private String clientId;
  }

  @Getter
  @Setter
  public static class GeoIp {
    private String databasePath;
  }
}
