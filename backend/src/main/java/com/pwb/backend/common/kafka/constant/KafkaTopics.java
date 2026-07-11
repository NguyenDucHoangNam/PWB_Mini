package com.pwb.backend.common.kafka.constant;

public final class KafkaTopics {

    public static final String IAM_USER_REGISTERED = "iam.user.registered.v1";
    public static final String IAM_OTP_RESENT = "iam.user.otp-resent.v1";
    public static final String IAM_PASSWORD_RESET = "iam.password.reset.v1";
    public static final String IAM_ACCOUNT_DELETION = "iam.account.deletion.v1";
    public static final String IAM_ACCOUNT_EVENTS = "iam-account-events";

    private KafkaTopics() {
    }
}