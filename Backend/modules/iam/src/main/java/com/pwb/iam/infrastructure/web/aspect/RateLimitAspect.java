package com.pwb.iam.infrastructure.web.aspect;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.core.exception.IamErrorCode;

import com.pwb.iam.infrastructure.security.annotation.RateLimited;
import com.pwb.iam.infrastructure.security.service.RateLimitService;
import com.pwb.iam.infrastructure.security.service.RateLimitService.RateLimitDecision;
import com.pwb.iam.infrastructure.security.util.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String clientIp = ClientIpResolver.getClientIp();
        RateLimitDecision decision = rateLimitService.check(rateLimited.endpoint(), clientIp);

        if (!decision.allowed()) {
            log.info("Rate limit denied: endpoint={} ip={} retryAfter={}s",
                    rateLimited.endpoint(), clientIp, decision.retryAfterSeconds());
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED, decision.retryAfterSeconds());
        }

        return joinPoint.proceed();
    }
}



