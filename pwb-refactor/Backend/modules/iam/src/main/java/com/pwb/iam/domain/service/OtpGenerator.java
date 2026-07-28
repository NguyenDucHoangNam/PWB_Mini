package com.pwb.iam.domain.service;

public interface OtpGenerator {

    String generate();

    String hash(String rawCode);

    boolean matches(String rawCode, String hashedCode);
}
