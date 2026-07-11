package com.pwb.backend.common.security.cookie;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cookie")
@Getter
@Setter
public class CookieProperties {

    private boolean secure = false;
    private String samesite = "Strict";
    private String refreshCookieName = "refreshToken";
    private String path = "/";
}
