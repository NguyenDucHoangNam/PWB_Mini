package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.shared.messaging.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AudioOutboxEventRepository extends OutboxEventRepository<AudioOutboxEvent> {

  Optional<AudioOutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
