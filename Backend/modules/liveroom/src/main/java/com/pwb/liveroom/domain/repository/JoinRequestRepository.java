package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.JoinRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestRepository {

    JoinRequest save(JoinRequest request);

    List<JoinRequest> saveAll(List<JoinRequest> requests);

    Optional<JoinRequest> findById(UUID id);


    Optional<JoinRequest> findByRoomIdAndUserIdAndIdempotencyKey(UUID roomId, UUID userId, String idempotencyKey);

    Optional<JoinRequest> findPendingByRoomIdAndUserId(UUID roomId, UUID userId);


    Optional<JoinRequest> findLatestByRoomIdAndUserId(UUID roomId, UUID userId);


    List<JoinRequest> findPendingByRoomId(UUID roomId);


    void deleteAllByRoomId(UUID roomId);


    int expireIdempotencyKeys(Instant expiredBefore);
}