package com.pwb.iam.domain.service;

public interface OtpGenerator {

    /**
     * @param length number of digits, driven by {@code OtpPolicy} rather than fixed here
     */
    String generate(int length);

    String hash(String rawCode);

    boolean matches(String rawCode, String hashedCode);
}
