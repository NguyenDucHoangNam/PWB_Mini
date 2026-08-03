package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.model.AvatarPolicy;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.model.PasswordResetPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamPolicyConfig {

    @Bean
    public PasswordResetPolicy passwordResetPolicy(PasswordResetProperties properties) {
        return new PasswordResetPolicy(
                properties.getTokenSecret(),
                properties.getTokenTtlMinutes(),
                properties.getCooldownSeconds(),
                properties.getFrontendUrl(),
                properties.getResetPath()
        );
    }

    @Bean
    public LoginPolicy loginPolicy(RateLimitProperties rateLimit, LoginPolicyProperties loginPolicy) {
        return new LoginPolicy(
                rateLimit.getLoginPerMinute(),
                rateLimit.getRefreshPerMinute(),
                rateLimit.getGoogleLoginPerMinute(),
                rateLimit.getResetPasswordPerMinute(),
                rateLimit.getVerifyOtpPerMinute(),
                rateLimit.getChangePasswordPerMinute(),
                loginPolicy.getMaxFailures(),
                loginPolicy.getLockMinutes()
        );
    }

    @Bean
    public AvatarPolicy avatarPolicy(AvatarProperties properties) {
        return new AvatarPolicy(
                properties.getMaxSizeBytes(),
                properties.allowedContentTypeSet(),
                properties.getUrlTtl()
        );
    }

    @Bean
    public OtpPolicy otpPolicy(OtpProperties properties) {
        return new OtpPolicy(
                properties.getTtlMinutes(),
                properties.getResendCooldownSeconds(),
                properties.getMaxAttempts(),
                properties.getCodeLength(),
                properties.getDailyLimit()
        );
    }
}
