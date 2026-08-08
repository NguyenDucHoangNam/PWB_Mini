package com.pwb.liveroom.infrastructure.persistence.specification;

import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class LiveRoomSpecifications {

    private LiveRoomSpecifications() {
    }

    public static Specification<LiveRoomJpaEntity> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<LiveRoomJpaEntity> hasStatus(RoomStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<LiveRoomJpaEntity> matchesNameOrCode(String keyword) {
        return (root, query, cb) -> cb.or(
                cb.like(root.get("normalizedName"), "%" + keyword.toLowerCase() + "%"),
                cb.equal(root.get("roomCode"), keyword.toUpperCase())
        );
    }

    public static Specification<LiveRoomJpaEntity> fromCriteria(RoomSearchCriteria criteria) {
        Specification<LiveRoomJpaEntity> spec = ownedBy(criteria.ownerId());

        if (criteria.status() != null) {
            spec = spec.and(hasStatus(criteria.status()));
        }
        if (criteria.hasKeyword()) {
            spec = spec.and(matchesNameOrCode(criteria.keyword()));
        }
        return spec;
    }
}