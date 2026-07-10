package com.pwb.backend.audio.internal.worker;

import com.pwb.backend.audio.internal.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationService {

  private final SimpMessagingTemplate simpMessagingTemplate;
  private final AudioProperties audioProperties;

  public void sendProcessingCompleted(String userId, String demoId, List<Float> waveformData) {
    String destination = audioProperties.getWebhook().getDemoStatusQueue();
    Map<String, Object> payload = Map.of(
        "eventType", "PROCESSING_COMPLETED",
        "demoId", demoId,
        "waveformData", waveformData == null ? List.of() : waveformData,
        "occurredAt", Instant.now().toString()
    );
    try {
      simpMessagingTemplate.convertAndSendToUser(userId, destination, payload);
      log.info("Sent PROCESSING_COMPLETED to user={}, demo={}", userId, demoId);
    } catch (Exception ex) {
      log.warn("Failed to send PROCESSING_COMPLETED to user={}: {}", userId, ex.getMessage());
    }
  }

  public void sendProcessingFailed(String userId, String demoId, String errorMessage) {
    String destination = audioProperties.getWebhook().getDemoStatusQueue();
    Map<String, Object> payload = Map.of(
        "eventType", "PROCESSING_FAILED",
        "demoId", demoId,
        "errorMessage", errorMessage,
        "occurredAt", Instant.now().toString()
    );
    try {
      simpMessagingTemplate.convertAndSendToUser(userId, destination, payload);
      log.info("Sent PROCESSING_FAILED to user={}, demo={}", userId, demoId);
    } catch (Exception ex) {
      log.warn("Failed to send PROCESSING_FAILED to user={}: {}", userId, ex.getMessage());
    }
  }
}
