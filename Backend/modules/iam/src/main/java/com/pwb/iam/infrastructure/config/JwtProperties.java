package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "pwb-iam";
    private String audience = "pwb-clients";
    private long accessTtlSeconds = 1800L; // 30 minutes
    private long clockSkewSeconds = 30L;
}