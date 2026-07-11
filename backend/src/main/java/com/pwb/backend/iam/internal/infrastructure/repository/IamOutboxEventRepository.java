package com.pwb.backend.iam.internal.infrastructure.repository;

import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.shared.messaging.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IamOutboxEventRepository extends OutboxEventRepository<IamOutboxEvent> {

  Optional<IamOutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
