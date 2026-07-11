package com.pwb.backend.audio.internal.application.helper;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class S3KeyBuilder {

  public String generateOriginalKey(String userId, String extension) {
    String uuid = UUID.randomUUID().toString();
    return "original/" + sanitize(userId) + "/" + uuid + "." + sanitize(extension);
  }

  public String generateConfirmedKey(String userId, String uuid, String extension) {
    return "original/confirmed/" + sanitize(userId) + "/" + sanitize(uuid) + "." + sanitize(extension);
  }

  public String generateStreamPlaylistKey(String demoId) {
    return "stream/" + sanitize(demoId) + "/playlist.m3u8";
  }

  public String generateStreamSegmentKey(String demoId, int index) {
    return "stream/" + sanitize(demoId) + "/seg_" + String.format("%03d", index) + ".ts";
  }

  private String sanitize(String value) {
    if (value == null) {
      throw new IllegalArgumentException("S3 key segment must not be null");
    }
    return value;
  }
}
