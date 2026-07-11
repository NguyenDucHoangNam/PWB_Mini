package com.pwb.backend.audio.internal.application.helper;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class MimeToExtensionMapper {

  private static final Map<String, String> MIME_TO_EXT = Map.of(
      "audio/wav", "wav",
      "audio/x-wav", "wav",
      "audio/wave", "wav",
      "audio/flac", "flac",
      "audio/x-flac", "flac",
      "audio/mpeg", "mp3",
      "audio/mp3", "mp3"
  );

  private static final Set<String> ALLOWED_EXTS = Set.of("wav", "flac", "mp3");

  public String toExtension(String mimeType) {
    if (mimeType == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT,
          "Content-Type is required");
    }
    String normalized = mimeType.trim().toLowerCase();
    String ext = MIME_TO_EXT.get(normalized);
    if (ext == null || !ALLOWED_EXTS.contains(ext)) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT,
          "Unsupported audio MIME type: " + mimeType + ". Accepted: wav, flac, mp3");
    }
    return ext;
  }

  public boolean isAllowed(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    String normalized = mimeType.trim().toLowerCase();
    return MIME_TO_EXT.containsKey(normalized);
  }
}
