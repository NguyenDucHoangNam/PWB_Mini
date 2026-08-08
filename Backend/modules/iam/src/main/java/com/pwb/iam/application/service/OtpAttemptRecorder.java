package com.pwb.iam.application.service;

import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpAttemptRecorder {

    private final OtpCodeRepository otpCodeRepository;
    private final OtpPolicy otpPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID otpId) {
        if (otpId == null) {
            return;
        }
        otpCodeRepository.incrementAttempts(otpId);
        boolean locked = otpCodeRepository.markLockedIfNotAlready(otpId, otpPolicy.maxAttempts());
        if (locked) {
            log.warn("OTP locked after reaching max attempts: otpId={} maxAttempts={}",
                    otpId, otpPolicy.maxAttempts());
        }
    }
}
