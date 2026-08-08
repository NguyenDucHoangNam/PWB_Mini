package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.service.OtpIssuer;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final ThrottlingService throttlingService;
    private final OtpIssuer otpIssuer;

    @Override
    @Transactional
    public void execute(ResendOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        long remaining = throttlingService.enforceCooldown(
                user.getEmail().value(), ThrottlingService.CooldownPurpose.RESEND_OTP);
        if (remaining > 0) {
            log.info("OTP resend throttled: userId={} remainingSeconds={}", user.getUserId(), remaining);
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    Map.of("cooldownSeconds", remaining));
        }

        otpIssuer.assertDailyQuotaAvailable(user, command.purpose());
        otpIssuer.issue(user, command.purpose(), command.locale());

        log.info("OTP resent: userId={} purpose={}", user.getUserId(), command.purpose());
    }
}
