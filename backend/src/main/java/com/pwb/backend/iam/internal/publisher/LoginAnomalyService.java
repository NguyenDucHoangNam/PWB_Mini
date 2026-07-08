package com.pwb.backend.iam.internal.publisher;

import com.pwb.backend.iam.internal.factory.OutboxEventFactory;
import com.pwb.backend.iam.internal.model.User;
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
  public void recordAnomalousLogin(User user, String ip, String location, String device) {
    outboxEventFactory.anomalousLogin(user, ip, location, device);
  }
}