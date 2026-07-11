package com.pwb.backend.iam.internal.infrastructure.publisher;

import com.pwb.backend.iam.internal.application.factory.OutboxEventFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAnomalyService {

  private final OutboxEventFactory outboxEventFactory;

  @Transactional
  public void recordAnomalousLogin(String userId, String email, String fullName,
                                   String ip, String location, String device) {
    outboxEventFactory.anomalousLogin(userId, email, fullName, ip, location, device);
  }
}
