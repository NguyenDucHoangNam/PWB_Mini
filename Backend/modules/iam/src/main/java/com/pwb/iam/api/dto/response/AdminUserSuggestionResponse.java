package com.pwb.iam.api.dto.response;

import com.pwb.iam.application.dto.AdminUserSuggestionView;

import java.util.UUID;

/**
 * A single search-as-you-type row for the admin user picker. Identity and display name only — a
 * suggestion list is not a place to hand out account detail.
 */
public record AdminUserSuggestionResponse(UUID id, String email, String fullName) {

    public static AdminUserSuggestionResponse from(AdminUserSuggestionView view) {
        return new AdminUserSuggestionResponse(view.id(), view.email(), view.fullName());
    }
}
