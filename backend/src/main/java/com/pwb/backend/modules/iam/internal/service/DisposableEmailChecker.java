package com.pwb.backend.modules.iam.internal.service;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DisposableEmailChecker {

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
    String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
    return BLOCKED_DOMAINS.contains(domain);
  }
}
