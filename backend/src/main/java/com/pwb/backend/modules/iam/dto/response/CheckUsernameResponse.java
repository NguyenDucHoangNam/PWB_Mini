package com.pwb.backend.modules.iam.dto.response;

public record CheckUsernameResponse(String username, boolean available) {
}