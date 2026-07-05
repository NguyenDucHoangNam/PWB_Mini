package com.pwb.backend.iam.internal.service;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DisposableEmailCheckerService {

  private static final Set<String> BLOCKED_DOMAINS = Set.of(
      "tempmail.com",
      "10minutemail.com",
      "yopmail.com",
      "mailinator.com",
      "guerrillamail.com",
      "throwaway.email",
      "trashmail.com",
      "sharklasers.com",
      "guerrillamailblock.com",
      "grr.la",
      "dispostable.com",
      "maildrop.cc",
      "fakeinbox.com",
      "temp-mail.org",
      "tempail.com",
      "mohmal.com"
  );

  public boolean isDisposable(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    int atIndex = email.indexOf('@');
    if (atIndex == -1 || atIndex == email.length() - 1) {
      return false;
    }
    String domain = email.substring(atIndex + 1).toLowerCase().trim();
    return BLOCKED_DOMAINS.contains(domain);
  }
}
