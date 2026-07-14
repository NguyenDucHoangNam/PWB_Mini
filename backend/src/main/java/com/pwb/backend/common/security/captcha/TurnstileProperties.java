package com.pwb.backend.common.security.captcha;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "app.security.captcha.turnstile")
@Getter
@Setter
public class TurnstileProperties {

    private boolean enabled;
    private String secretKey;
    private String siteKey;
    private String verifyUrl;
    private long timeoutMillis;
    private boolean failOpen;

    @NestedConfigurationProperty
    private Adaptive adaptive = new Adaptive();

    @Getter
    @Setter
    public static class Adaptive {
        private boolean enabled = true;
        private int failureThreshold = 3;
        private long trackingWindowSeconds = 900;
        private boolean bypassOnSuccess = true;
        private boolean alwaysRequired = false;
    }
}
