package com.pwb.backend.shared.outbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

  private boolean enabled = true;
  private int batchSize = 20;
  private int pollIntervalMs = 30000;
  private int deadLetterAfterRetries = 5;
  private int initialBackoffSeconds = 1;
  private int maxBackoffSeconds = 60;
  private String encryptionKey;
  private int keyVersion = 1;
}
