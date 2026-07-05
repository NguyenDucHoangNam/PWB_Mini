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
}
