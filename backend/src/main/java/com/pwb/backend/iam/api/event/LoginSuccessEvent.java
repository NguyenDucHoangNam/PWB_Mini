package com.pwb.backend.iam.api.event;

import org.springframework.context.ApplicationEvent;

public class LoginSuccessEvent extends ApplicationEvent {

  private final String userId;
  private final String ipAddress;
  private final String userAgent;

  public LoginSuccessEvent(Object source, String userId, String ipAddress, String userAgent) {
    super(source);
    this.userId = userId;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
  }

  public String getUserId() {
    return userId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }
}
