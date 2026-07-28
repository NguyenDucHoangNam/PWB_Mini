package com.pwb.iam.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.iam.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.iam.audit.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuditKafkaConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.iam-audit:iam.audit.v1}",
            groupId = "${pwb.iam.audit.consumer.group:iam-audit-consumer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAudit(ConsumerRecord<String, String> record) {
        try {
            AuditLogEntry entry = objectMapper.readValue(record.value(), AuditLogEntry.class);
            auditLogRepository.save(entry);
            log.debug("Audit persisted: eventId={} type={}", entry.eventId(), entry.eventType());
        } catch (Exception ex) {
            log.warn("Audit persist failed: key={} reason={}", record.key(), ex.getMessage());
            throw new RuntimeException("Audit persist failed", ex);
        }
    }
}