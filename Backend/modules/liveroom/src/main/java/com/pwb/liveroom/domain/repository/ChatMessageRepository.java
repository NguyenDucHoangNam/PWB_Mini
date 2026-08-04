package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.ChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);


    Optional<ChatMessage> findByIdAndCycleId(UUID id, UUID cycleId);


    List<ChatMessage> findLatestByCycleId(UUID cycleId, int limit);


    List<ChatMessage> findOlderByCycleId(UUID cycleId, Instant beforeSentAt, UUID beforeId, int limit);
}