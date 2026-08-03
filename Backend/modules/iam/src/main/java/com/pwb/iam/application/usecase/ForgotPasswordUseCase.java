package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ForgotPasswordCommand;

public interface ForgotPasswordUseCase {

    Result execute(ForgotPasswordCommand command);

    record Result(java.util.UUID userId, String status, int cooldownSeconds) {

        public static Result sent(java.util.UUID userId, int cooldownSeconds) {
            return new Result(userId, "SENT", cooldownSeconds);
        }

        public static Result cooldown(java.util.UUID userId, int cooldownSeconds) {
            return new Result(userId, "COOLDOWN", cooldownSeconds);
        }
    }
}