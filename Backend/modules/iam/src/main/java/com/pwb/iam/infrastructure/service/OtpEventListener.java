package com.pwb.iam.infrastructure.service;

import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.event.AuthEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpEventListener {

    private final AuthEventPublisher authEventPublisher;

    @EventListener
    public void onOtpIssued(OtpIssuedDomainEvent event) {
        if (event.purpose() == OtpPurpose.REGISTER) {
            log.debug("OTP issued for registration: userId={} email={}",
                    event.userId(), event.email());
            authEventPublisher.publishUserRegisteredOtp(
                    event.userId(),
                    event.email(),
                    event.otpCode());
        }
    }
}
