package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.ChatMessage;
import com.pwb.liveroom.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageJpaEntity toEntity(ChatMessage domain) {
        if (domain == null) {
            return null;
        }
        return ChatMessageJpaEntity.builder()
                .roomId(domain.getRoomId())
                .cycleId(domain.getCycleId())
                .userId(domain.getUserId())
                .userEmail(domain.getUserEmail())
                .content(domain.getContent())
                .sentAt(domain.getSentAt())
                .build();
    }

    public ChatMessage toDomain(ChatMessageJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        ChatMessage domain = ChatMessage.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getCycleId(),
                entity.getUserId(),
                entity.getUserEmail(),
                entity.getContent(),
                entity.getSentAt()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}