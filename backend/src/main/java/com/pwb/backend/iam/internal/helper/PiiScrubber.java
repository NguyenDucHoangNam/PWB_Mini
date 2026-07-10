package com.pwb.backend.iam.internal.helper;

public class PiiScrubber {

    private PiiScrubber() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "<none>";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "**" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "<none>";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) return "***";
        return "***" + digits.substring(digits.length() - 4);
    }

    public static String userRef(String userId) {
        if (userId == null || userId.isBlank()) return "<none>";
        if (userId.length() <= 6) return "***";
        return userId.substring(0, 4) + "..." + userId.substring(userId.length() - 4);
    }
}