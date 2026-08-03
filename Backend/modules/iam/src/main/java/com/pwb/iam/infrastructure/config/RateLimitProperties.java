package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.rate-limit")
public class RateLimitProperties {

    private int loginPerMinute = 10;
    private int refreshPerMinute = 30;
    private int googleLoginPerMinute = 10;
    private int resetPasswordPerMinute = 5;
    private int verifyOtpPerMinute = 10;
    private int changePasswordPerMinute = 5;

    /**
     * When Redis is unavailable: deny the request instead of letting it through.
     * Defaults to true — a rate limiter that silently disables itself during an outage
     * offers no protection exactly when it is most likely to be probed.
     */
    private boolean failClosedForCriticalOps = true;
}