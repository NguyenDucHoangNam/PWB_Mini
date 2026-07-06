package com.pwb.backend.iam.api.dto.request;

public record DeleteAccountRequest(
    String password,
    String idToken
) {}
