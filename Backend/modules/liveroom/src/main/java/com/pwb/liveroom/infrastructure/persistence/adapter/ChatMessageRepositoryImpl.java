package com.pwb.liveroom.infrastructure.persistence.adapter;

import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.liveroom.domain.repository.ChatMessageRepository;
import com.pwb.liveroom.infrastructure.persistence.mapper.ChatMessageMapper;
import com.pwb.liveroom.infrastructure.persistence.repository.ChatMessageJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageJpaRepository chatMessageJpaRepository;
    private final ChatMessageMapper chatMessageMapper;


    @Override
    public ChatMessage save(ChatMessage message) {
        if (!message.isNew()) {
            throw new IllegalStateException("Chat messages cannot be modified after being sent");
        }
        return chatMessageMapper.toDomain(
                chatMessageJpaRepository.save(chatMessageMapper.toEntity(message)));
    }

    @Override
    public Optional<ChatMessage> findByIdAndCycleId(UUID id, UUID cycleId) {
        return chatMessageJpaRepository.findByIdAndCycleId(id, cycleId)
                .map(chatMessageMapper::toDomain);
    }

    @Override
    public List<ChatMessage> findLatestByCycleId(UUID cycleId, int limit) {
        return chatMessageJpaRepository
                .findAllByCycleIdOrderBySentAtDescIdDesc(cycleId, limit(limit)).stream()
                .map(chatMessageMapper::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> findOlderByCycleId(UUID cycleId, Instant beforeSentAt, UUID beforeId, int limit) {
        return chatMessageJpaRepository
                .findOlderThan(cycleId, beforeSentAt, beforeId, limit(limit)).stream()
                .map(chatMessageMapper::toDomain)
                .toList();
    }

    private Pageable limit(int limit) {
        return PageRequest.ofSize(limit);
    }
}