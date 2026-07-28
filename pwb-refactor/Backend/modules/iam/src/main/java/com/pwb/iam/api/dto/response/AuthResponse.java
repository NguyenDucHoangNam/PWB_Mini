package com.pwb.iam.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public class AuthResponse {

    private UUID userId;
    private String email;
    private String username;
    private String status;
    private String role;
    private String tokenType = "Bearer";
    private long expiresIn;
    @JsonIgnore
    private String message;

    public AuthResponse() {
    }

    public static AuthResponse bearerOnly(UUID userId, String email, String username, String status, String role) {
        AuthResponse response = new AuthResponse();
        response.userId = userId;
        response.email = email;
        response.username = username;
        response.status = status;
        response.role = role;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
