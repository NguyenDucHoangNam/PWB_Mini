package com.pwb.backend.modules.iam.api.dto.response;

public record VerifyOtpResponse(
    String accessToken,
    long expiresIn,
    UserInfo user
) {
  public record UserInfo(
      String username,
      String email,
      String fullName,
      String status
  ) {}
}
