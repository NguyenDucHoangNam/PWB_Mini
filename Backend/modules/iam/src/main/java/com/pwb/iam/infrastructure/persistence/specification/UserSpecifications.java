package com.pwb.iam.infrastructure.persistence.specification;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserJpaEntity> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<UserJpaEntity> hasStatus(UserStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<UserJpaEntity> hasRole(String roleName) {
        return (root, query, cb) -> {
            Join<Object, Object> role = root.join("role", JoinType.INNER);
            return cb.equal(role.get("name"), roleName);
        };
    }

    public static Specification<UserJpaEntity> hasProvider(OAuthProvider provider) {
        return (root, query, cb) -> cb.equal(root.get("oauthProvider"), provider);
    }

    public static Specification<UserJpaEntity> keywordMatch(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern)
            );
        };
    }

    public static Specification<UserJpaEntity> fromCriteria(UserSearchCriteria criteria) {
        Specification<UserJpaEntity> spec = notDeleted();

        if (criteria.status() != null) {
            spec = spec.and(hasStatus(criteria.status()));
        }
        if (criteria.role() != null) {
            spec = spec.and(hasRole(criteria.role().name()));
        }
        if (criteria.provider() != null) {
            spec = spec.and(hasProvider(criteria.provider()));
        }
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            spec = spec.and(keywordMatch(criteria.keyword()));
        }
        return spec;
    }
}