package com.pwb.audio.infrastructure.persistence.specification;

import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.repository.VoiceTagSearchCriteria;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * The database stand-in for a voice tag search. Same trade-off as {@link SongSpecifications}: correct
 * rows, plainer matching.
 */
public final class VoiceTagSpecifications {

    private VoiceTagSpecifications() {
    }

    public static Specification<VoiceTagJpaEntity> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<VoiceTagJpaEntity> nameContains(String keyword) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<VoiceTagJpaEntity> hasTagType(VoiceTagType tagType) {
        return (root, query, cb) -> cb.equal(root.get("tagType"), tagType);
    }

    public static Specification<VoiceTagJpaEntity> hasLanguageCode(String languageCode) {
        return (root, query, cb) -> cb.equal(root.get("languageCode"), languageCode);
    }

    public static Specification<VoiceTagJpaEntity> fromCriteria(VoiceTagSearchCriteria criteria) {
        Specification<VoiceTagJpaEntity> spec = ownedBy(criteria.userId());

        if (criteria.hasKeyword()) {
            spec = spec.and(nameContains(criteria.keyword()));
        }
        if (criteria.tagType() != null) {
            spec = spec.and(hasTagType(criteria.tagType()));
        }
        if (criteria.languageCode() != null && !criteria.languageCode().isBlank()) {
            spec = spec.and(hasLanguageCode(criteria.languageCode()));
        }
        return spec;
    }
}
