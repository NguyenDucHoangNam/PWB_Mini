package com.pwb.backend.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

    private String refreshName = "refresh_token";

    private boolean secure = false;

    private String sameSite = "Lax";

    private int maxAgeSeconds = 604800;

    private String path = "/";
}
