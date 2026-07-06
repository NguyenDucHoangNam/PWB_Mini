package com.pwb.backend.iam.api.dto.response;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    UserInfo user
) {
  public record UserInfo(
      String username,
      String email,
      String fullName,
      String role,
      String status
  ) {}
}
