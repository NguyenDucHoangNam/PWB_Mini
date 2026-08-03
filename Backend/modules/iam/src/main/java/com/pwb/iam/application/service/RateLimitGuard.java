package com.pwb.iam.application.service;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitGuard {

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final ThrottlingService throttlingService;

    public void check(String key, int limit) {
        check(key, limit, DEFAULT_WINDOW);
    }

    public void check(String key, int limit, Duration window) {
        ThrottlingService.ThrottleDecision decision = throttlingService.consume(key, limit, window);
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }
    }

    public void checkIpAndSubject(String scope, String clientIp, String subject, int limit) {
        check(scope + ":ip:" + clientIp, limit);
        check(scope + ":subject:" + subject, limit);
    }
}
