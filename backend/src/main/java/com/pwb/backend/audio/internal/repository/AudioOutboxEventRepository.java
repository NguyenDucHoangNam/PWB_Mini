package com.pwb.backend.audio.internal.repository;

import com.pwb.backend.audio.internal.model.AudioOutboxEvent;
import com.pwb.backend.shared.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AudioOutboxEventRepository extends OutboxEventRepository<AudioOutboxEvent> {

  Optional<AudioOutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
