package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.domain.enums.ParticipantState;
import com.pwb.liveroom.infrastructure.persistence.entity.ParticipantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantJpaEntity, UUID> {

    Optional<ParticipantJpaEntity> findByCycleIdAndUserId(UUID cycleId, UUID userId);

    List<ParticipantJpaEntity> findAllByCycleIdAndStateInOrderByJoinedAtAscUserIdAsc(
            UUID cycleId, Collection<ParticipantState> states);

    List<ParticipantJpaEntity> findAllByCycleIdOrderByJoinedAtAsc(UUID cycleId);

    List<ParticipantJpaEntity> findAllByCycleIdAndStateAndLeftAt(
            UUID cycleId, ParticipantState state, Instant leftAt);
}