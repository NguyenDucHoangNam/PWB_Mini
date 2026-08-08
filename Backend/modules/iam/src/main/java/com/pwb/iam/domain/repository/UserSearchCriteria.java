package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.UserStatus;

public record UserSearchCriteria(
        String keyword,
        UserStatus status,
        RoleName role,
        OAuthProvider provider
) {

    public static UserSearchCriteria empty() {
        return new UserSearchCriteria(null, null, null, null);
    }
}