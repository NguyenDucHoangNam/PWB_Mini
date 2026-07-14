package com.pwb.backend.common.security.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.captcha.turnstile")
@Getter
@Setter
public class TurnstileProperties {

    private boolean enabled;
    private String secretKey;
    private String verifyUrl;
    private long timeoutMillis;
    private boolean failOpen;
}
