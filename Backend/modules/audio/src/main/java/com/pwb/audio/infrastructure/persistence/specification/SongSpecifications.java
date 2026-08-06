package com.pwb.audio.infrastructure.persistence.specification;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.repository.SongSearchCriteria;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * The database stand-in for a song search, used when Elasticsearch is unreachable.
 *
 * <p>It matches on a substring of the title, which is a plainer thing than what the index does: no
 * tolerance for typos, and no matching "Hà Nội" from "ha noi", because a {@code LIKE} compares the
 * characters as stored. Every filter is still applied, so the result is a narrower set of the right
 * rows rather than the wrong ones.
 */
public final class SongSpecifications {

    private SongSpecifications() {
    }

    public static Specification<SongJpaEntity> ownedBy(java.util.UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<SongJpaEntity> titleContains(String keyword) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<SongJpaEntity> statusIn(java.util.Collection<SongStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<SongJpaEntity> hasFormat(String format) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("format")), format.toLowerCase());
    }

    public static Specification<SongJpaEntity> durationBetween(Integer min, Integer max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("durationSeconds"), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("durationSeconds"), min);
            }
            return cb.lessThanOrEqualTo(root.get("durationSeconds"), max);
        };
    }

    public static Specification<SongJpaEntity> fromCriteria(SongSearchCriteria criteria) {
        Specification<SongJpaEntity> spec = ownedBy(criteria.userId());

        if (criteria.hasKeyword()) {
            spec = spec.and(titleContains(criteria.keyword()));
        }
        if (!criteria.statuses().isEmpty()) {
            spec = spec.and(statusIn(criteria.statuses()));
        }
        if (criteria.format() != null && !criteria.format().isBlank()) {
            spec = spec.and(hasFormat(criteria.format()));
        }
        if (criteria.minDurationSeconds() != null || criteria.maxDurationSeconds() != null) {
            spec = spec.and(durationBetween(criteria.minDurationSeconds(), criteria.maxDurationSeconds()));
        }
        return spec;
    }
}
