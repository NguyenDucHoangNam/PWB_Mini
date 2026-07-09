package com.pwb.backend.iam.api.dto.response;

public record VerifyOtpResponse(
    String accessToken,
    long expiresIn,
    UserInfo user
) {
  public record UserInfo(
      String username,
      String email,
      String fullName,
      String status,
      String oauthProvider
  ) {}
}