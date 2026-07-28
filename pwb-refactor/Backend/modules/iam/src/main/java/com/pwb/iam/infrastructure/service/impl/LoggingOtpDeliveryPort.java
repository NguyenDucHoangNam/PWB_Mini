package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.OtpDeliveryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class LoggingOtpDeliveryPort implements OtpDeliveryPort {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    @Override
    public DeliveryResult deliver(UUID userId, String email, String purpose) {
        log.info("OTP delivered: userId={} email={} purpose={}", userId, email, purpose);
        return DeliveryResult.ok(COOLDOWN);
    }
}
