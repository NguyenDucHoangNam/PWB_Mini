package com.pwb.iam.application.dto;

import java.util.UUID;

public record AdminUserSuggestionView(UUID id, String email, String fullName) {
}
