package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.enums.ParticipantState;
import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.domain.repository.ParticipantRepository;
import com.pwb.liveroom.infrastructure.persistence.entity.ParticipantJpaEntity;
import com.pwb.liveroom.infrastructure.persistence.mapper.ParticipantMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.ParticipantJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ParticipantRepositoryImpl implements ParticipantRepository {

    private static final Set<ParticipantState> IN_ROOM =
            Set.of(ParticipantState.ACTIVE, ParticipantState.RECONNECTING);

    private final ParticipantJpaRepository participantJpaRepository;
    private final ParticipantMapper participantMapper;

    @Override
    public Participant save(Participant participant) {
        return participantMapper.toDomain(participantJpaRepository.save(toManaged(participant)));
    }

    @Override
    public List<Participant> saveAll(List<Participant> participants) {
        List<ParticipantJpaEntity> entities = participants.stream()
                .map(this::toManaged)
                .toList();
        return participantJpaRepository.saveAll(entities).stream()
                .map(participantMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Participant> findByCycleIdAndUserId(UUID cycleId, UUID userId) {
        return participantJpaRepository.findByCycleIdAndUserId(cycleId, userId)
                .map(participantMapper::toDomain);
    }

    @Override
    public List<Participant> findInRoomByCycleId(UUID cycleId) {
        return participantJpaRepository
                .findAllByCycleIdAndStateInOrderByJoinedAtAscUserIdAsc(cycleId, IN_ROOM).stream()
                .map(participantMapper::toDomain)
                .toList();
    }

    @Override
    public List<Participant> findAllByCycleId(UUID cycleId) {
        return participantJpaRepository.findAllByCycleIdOrderByJoinedAtAsc(cycleId).stream()
                .map(participantMapper::toDomain)
                .toList();
    }

    @Override
    public List<Participant> findClosedWithRoomAt(UUID cycleId, Instant leftAt) {
        return participantJpaRepository
                .findAllByCycleIdAndStateAndLeftAt(cycleId, ParticipantState.ENDED, leftAt).stream()
                .map(participantMapper::toDomain)
                .toList();
    }

    private ParticipantJpaEntity toManaged(Participant participant) {
        if (participant.isNew()) {
            return participantMapper.toEntity(participant);
        }
        ParticipantJpaEntity target = participantJpaRepository.findById(participant.getId())
                .orElseThrow(() -> new IllegalStateException("Participant no longer exists: " + participant.getId()));
        participantMapper.applyTo(participant, target);
        return target;
    }
}