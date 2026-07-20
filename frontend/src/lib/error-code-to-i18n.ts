import type { ApiError } from "@/lib/api-client";

export const ERROR_CODE_TO_I18N_KEY: Record<string, string> = {
  VOICE_001: "voice.errors.voiceTagNotFound",
  VOICE_002: "voice.errors.songNotFound",
  VOICE_003: "voice.errors.unsupportedFormat",
  VOICE_004: "voice.errors.fileTooLarge",
  VOICE_005: "voice.errors.processingFailed",
  VOICE_006: "voice.errors.processingFailed",
  VOICE_007: "voice.errors.intervalInvalid",
  VOICE_008: "voice.errors.duplicateVoiceTagName",
  VOICE_009: "voice.errors.streamNotReady",
  VOICE_010: "voice.errors.proOnly",
  VOICE_011: "voice.errors.voiceTagInUse",
  VOICE_012: "voice.errors.textTooLong",
  VOICE_013: "voice.errors.invalidLanguageCode",
  VOICE_015: "voice.errors.processingInProgress",
  VOICE_TAG_IN_USE: "voice.errors.voiceTagInUse",
};

export function resolveErrorI18nKey(error: ApiError | Error | null | undefined): string | null {
  if (!error) return null;
  if (!("code" in error) || !error.code) return null;
  return ERROR_CODE_TO_I18N_KEY[error.code] ?? null;
}
