package com.pwb.infra.outbox.sink;

import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;

public interface OutboxPublisher {

    void publish(OutboxEventJpaEntity event);
}