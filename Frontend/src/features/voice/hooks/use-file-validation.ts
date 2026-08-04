"use client";

import { useCallback } from "react";
import { useTranslations } from "next-intl";

export const MAX_AUDIO_FILE_SIZE = 100 * 1024 * 1024;

const ALLOWED_AUDIO_EXTENSIONS = ["mp3", "wav", "flac"];
const ALLOWED_AUDIO_MIME_TYPES = [
  "audio/mpeg",
  "audio/mp3",
  "audio/wav",
  "audio/wave",
  "audio/x-wav",
  "audio/flac",
  "audio/x-flac",
] as const;

export function useFileValidation() {
  const t = useTranslations("voice.errors");

  const validateAudioFile = useCallback(
    (file: File): string | null => {
      if (file.size > MAX_AUDIO_FILE_SIZE) {
        return t("fileTooLarge");
      }
      const ext = file.name.split(".").pop()?.toLowerCase();
      if (!ext || !ALLOWED_AUDIO_EXTENSIONS.includes(ext)) {
        return t("unsupportedFormat");
      }
      if (
        file.type &&
        !ALLOWED_AUDIO_MIME_TYPES.includes(
          file.type as (typeof ALLOWED_AUDIO_MIME_TYPES)[number]
        )
      ) {
        return t("unsupportedFormat");
      }
      return null;
    },
    [t]
  );

  return {
    validateAudioFile,
    MAX_AUDIO_FILE_SIZE,
  };
}