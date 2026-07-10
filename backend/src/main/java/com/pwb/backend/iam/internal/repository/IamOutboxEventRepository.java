package com.pwb.backend.iam.internal.repository;

import com.pwb.backend.iam.internal.model.IamOutboxEvent;
import com.pwb.backend.shared.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamOutboxEventRepository extends OutboxEventRepository<IamOutboxEvent> {
}
