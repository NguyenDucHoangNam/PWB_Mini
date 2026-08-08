package com.pwb.iam.domain.service;

import java.util.UUID;

public record UserSuggestion(UUID id, String email, String fullName) {
}
