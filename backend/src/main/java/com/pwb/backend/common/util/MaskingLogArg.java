package com.pwb.backend.common.util;

public final class MaskingLogArg {

    private static final String MASK = "***";
    private static final int PREFIX_VISIBLE = 8;

    private MaskingLogArg() {
    }

    public static String token(String value) {
        if (value == null || value.isBlank()) {
            return MASK;
        }
        if (value.length() <= PREFIX_VISIBLE) {
            return MASK;
        }
        return value.substring(0, PREFIX_VISIBLE) + MASK;
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return MASK;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return MASK;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String ip(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        int firstDot = ip.indexOf('.');
        if (firstDot < 0) {
            return MASK;
        }
        return ip.substring(0, firstDot) + ".***";
    }
}
