package com.pwb.backend.audio.internal.api;

import java.time.Instant;
import java.util.List;

public record VoiceTagListResponse(
    List<Item> items
) {
  public record Item(
      String id,
      String textContent,
      String languageCode,
      String voiceName,
      boolean isDefault,
      Instant createdAt
  ) {}
}
