package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.Participant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository {

    Participant save(Participant participant);

    List<Participant> saveAll(List<Participant> participants);

    Optional<Participant> findByCycleIdAndUserId(UUID cycleId, UUID userId);


    List<Participant> findInRoomByCycleId(UUID cycleId);

    List<Participant> findAllByCycleId(UUID cycleId);


    List<Participant> findClosedWithRoomAt(UUID cycleId, Instant leftAt);
}