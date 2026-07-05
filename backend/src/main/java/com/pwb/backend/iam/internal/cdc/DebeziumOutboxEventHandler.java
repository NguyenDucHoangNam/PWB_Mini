package com.pwb.backend.iam.internal.cdc;
 
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.publisher.OutboxPublisher;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import io.debezium.engine.RecordChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;
 
import java.util.Optional;
 
@Slf4j
@Component
@RequiredArgsConstructor
public class DebeziumOutboxEventHandler {
 
  private final OutboxEventRepository outboxEventRepository;
  private final OutboxPublisher outboxPublisher;
 
  public void handleEvent(RecordChangeEvent<SourceRecord> event) {
    SourceRecord sourceRecord = event.record();
    Struct value = (Struct) sourceRecord.value();
 
    if (value == null) {
      return;
    }
 
    Struct after = value.getStruct("after");
    String op = value.getString("op");
 
    if (after != null && ("c".equals(op) || "r".equals(op))) {
      try {
        String eventId = after.getString("id");
        Optional<OutboxEvent> outboxEventOpt = outboxEventRepository.findById(eventId);
 
        if (outboxEventOpt.isPresent()) {
          OutboxEvent outboxEvent = outboxEventOpt.get();
          if (outboxEvent.getStatus() == OutboxEventStatus.PENDING) {
            log.info("CDC received pending outbox event: {}", eventId);
            outboxPublisher.processOutboxEvent(outboxEvent);
          }
        }
      } catch (Exception e) {
        log.error("Failed to process CDC event", e);
      }
    }
  }
}
