package com.pwb.backend.common.outbox.publisher;

public final class OutboxEventTypes {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String OTP_RESENT = "OTP_RESENT";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
    public static final String ACCOUNT_DELETION_CANCELLED = "ACCOUNT_DELETION_CANCELLED";
    public static final String ACCOUNT_ANONYMIZED = "ACCOUNT_ANONYMIZED";

    public static final String SEND_SHARE_EMAIL = "SEND_SHARE_EMAIL";
    public static final String SEND_REVOKE_NOTICE = "SEND_REVOKE_NOTICE";

    public static final String AGGREGATE_USER = "User";
    public static final String AGGREGATE_DEMO_DISTRIBUTION = "demo_distribution";

    private OutboxEventTypes() {
    }
}