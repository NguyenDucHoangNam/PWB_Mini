package com.pwb.backend.common.security.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.captcha.turnstile")
@Getter
@Setter
public class TurnstileProperties {

    private boolean enabled = false;
    private String secretKey = "";
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private long timeoutMillis = 3000;
    private boolean failOpen = true;
}
