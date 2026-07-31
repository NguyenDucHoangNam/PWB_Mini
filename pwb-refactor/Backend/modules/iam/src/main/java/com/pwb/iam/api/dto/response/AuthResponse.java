package com.pwb.iam.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private UUID userId;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String status;
    private String role;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String accessToken;
    private String refreshToken;
    private String nextStep;

    public AuthResponse() {
    }

    public static AuthResponse bearerOnly(UUID userId, String email, String fullName, String avatarUrl, String status, String role) {
        AuthResponse response = new AuthResponse();
        response.userId = userId;
        response.email = email;
        response.fullName = fullName;
        response.avatarUrl = avatarUrl;
        response.status = status;
        response.role = role;
        return response;
    }

    public static AuthResponse tokens(UUID userId, String email, String fullName, String avatarUrl, String status, String role,
                                      String accessToken, String refreshToken, long expiresIn, String nextStep) {
        AuthResponse response = bearerOnly(userId, email, fullName, avatarUrl, status, role);
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.expiresIn = expiresIn;
        response.nextStep = nextStep;
        return response;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }
}
