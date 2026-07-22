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
  LIVEROOM_001: "liveroom.errors.roomNotFound",
  LIVEROOM_002: "liveroom.errors.codeExists",
  LIVEROOM_003: "liveroom.errors.hostAlreadyActive",
  LIVEROOM_006: "liveroom.errors.notHost",
  LIVEROOM_007: "liveroom.errors.alreadyEnded",
  LIVEROOM_008: "liveroom.errors.codeGenFailed",
  LIVEROOM_009: "liveroom.errors.invalidCapacity",
  LIVEROOM_011: "liveroom.errors.full",
  LIVEROOM_012: "liveroom.errors.notJoined",
  LIVEROOM_013: "liveroom.errors.alreadyJoined",
  LIVEROOM_020: "liveroom.errors.joinRequestNotFound",
  LIVEROOM_021: "liveroom.errors.joinRequestAlreadyDecided",
  LIVEROOM_022: "liveroom.errors.joinRequestNotPending",
  LIVEROOM_023: "liveroom.errors.joinRequestNotOwner",
  LIVEROOM_024: "liveroom.errors.generic",
};

export function resolveErrorI18nKey(error: ApiError | Error | null | undefined): string | null {
  if (!error) return null;
  if (!("code" in error) || !error.code) return null;
  return ERROR_CODE_TO_I18N_KEY[error.code] ?? null;
}
