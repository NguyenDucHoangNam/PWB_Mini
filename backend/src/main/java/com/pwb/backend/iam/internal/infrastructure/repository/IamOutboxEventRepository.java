package com.pwb.backend.iam.internal.infrastructure.repository;

import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.shared.messaging.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamOutboxEventRepository extends OutboxEventRepository<IamOutboxEvent> {
}
