package com.pwb.iam.infrastructure.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

    private String refreshName = "refresh_token";

    private boolean secure = false;

    private String sameSite = "Lax";

    private int maxAgeSeconds = 604800;

    private String path = "/";
}
