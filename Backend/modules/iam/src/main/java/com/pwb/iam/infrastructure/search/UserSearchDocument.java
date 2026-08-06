package com.pwb.iam.infrastructure.search;

import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * What a user looks like inside the index. Built from the JPA entity rather than the domain aggregate
 * because {@code User.rehydrate} does not carry the audit timestamps, and the index sorts on them.
 *
 * <p>Phone is not indexed, by request. Nothing secret ever is: no password hash, no OAuth id, no token.
 * An index is a second copy of the data with its own access path, so anything put here has to be
 * something the admin search is allowed to return.
 */
public record UserSearchDocument(
        UUID id,
        String email,
        String fullName,
        String status,
        String roleName,
        String oauthProvider,
        boolean deleted,
        Instant createdAt
) {

    public static UserSearchDocument from(UserJpaEntity entity) {
        return new UserSearchDocument(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getRole() == null ? null : entity.getRole().getName(),
                entity.getOauthProvider() == null ? null : entity.getOauthProvider().name(),
                entity.isDeleted(),
                entity.getCreatedAt()
        );
    }
}
