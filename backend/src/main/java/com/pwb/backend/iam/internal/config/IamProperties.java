package com.pwb.backend.iam.internal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Configuration
@ConfigurationProperties(prefix = "app.iam")
@Validated
public class IamProperties {

  private Jwt jwt = new Jwt();
  private Otp otp = new Otp();
  private Login login = new Login();
  private int accountDeletionGraceDays = 30;
  private Outbox outbox = new Outbox();
  private Anonymization anonymization = new Anonymization();
  private Google google = new Google();
  private GeoIp geoIp = new GeoIp();
  private Session session = new Session();

  @Getter
  public static class Session {
    private boolean cookieSecure = true;
    @NotBlank
    @Pattern(regexp = "Lax|Strict|None",
        message = "cookieSameSite must be one of: Lax, Strict, None")
    private String cookieSameSite = "Lax";
  }

  @Getter
  public static class Jwt {
    @NotBlank
    private String secret;
    @Min(1)
    private long accessTokenExpiration;
    @Min(1)
    private long refreshTokenExpiration;
  }

  @Getter
  public static class Otp {
    @Min(1)
    private long expiration;
    @Min(1)
    private long cooldown;
    @Min(1)
    private int maxAttempts;
  }

  @Getter
  public static class Login {
    @Min(1)
    private int maxFailedAttempts;
    @Min(1)
    private long lockoutTtlSeconds;
    @Min(1)
    private long passwordResetTokenTtlSeconds;
    @Valid
    private Lockout lockout = new Lockout();

    @Getter
    public static class Lockout {
      @Min(1)
      private int maxAttempts = 5;
      @Min(1)
      private long windowDurationMinutes = 15;
    }
  }

  @Getter
  public static class Outbox {
    @Min(1)
    private int deadLetterAfterRetries;
  }

  @Getter
  public static class Anonymization {
    @NotBlank
    private String cron;
  }

  @Getter
  public static class Google {
    @NotBlank
    private String clientId;
  }

  @Getter
  public static class GeoIp {
    @NotBlank
    private String databasePath;
  }
}
