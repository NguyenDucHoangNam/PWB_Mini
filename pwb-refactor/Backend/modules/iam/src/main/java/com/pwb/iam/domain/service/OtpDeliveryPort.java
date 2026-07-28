package com.pwb.iam.domain.service;

import java.time.Duration;
import java.util.UUID;

public interface OtpDeliveryPort {

    DeliveryResult deliver(UUID userId, String email, String purpose, String code);

    record DeliveryResult(boolean delivered, Duration cooldown) {
        public static DeliveryResult ok(Duration cooldown) {
            return new DeliveryResult(true, cooldown);
        }

        public static DeliveryResult throttled(Duration cooldown) {
            return new DeliveryResult(false, cooldown);
        }
    }
}