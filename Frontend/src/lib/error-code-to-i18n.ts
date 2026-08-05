import type { ApiError } from "@/lib/api-client";

const EXACT_ERROR_CODE_TO_I18N_KEY: Record<string, string> = {
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

  LR_001: "liveroom.errors.roomNotFound",
  LR_002: "liveroom.errors.roomEnded",
  LR_003: "liveroom.errors.roomNameDuplicate",
  LR_004: "liveroom.errors.roomNameInvalid",
  LR_005: "liveroom.errors.codeGenerationFailed",
  LR_006: "liveroom.errors.codeThrottled",
  LR_007: "liveroom.errors.cannotReopen",
  LR_008: "liveroom.errors.undoWindowExpired",
  LR_009: "liveroom.errors.graceInvalid",
  LR_010: "liveroom.errors.forceEndedRoleChange",
  LR_020: "liveroom.errors.notOwner",
  LR_021: "liveroom.errors.proRequired",
  LR_022: "liveroom.errors.selfJoin",
  LR_030: "liveroom.errors.requestNotFound",
  LR_031: "liveroom.errors.requestExpired",
  LR_032: "liveroom.errors.requestLocked",
  LR_033: "liveroom.errors.duplicateRequest",
  LR_034: "liveroom.errors.alreadyApproved",
  LR_040: "liveroom.errors.roomFull",
  LR_041: "liveroom.errors.alreadyInRoom",
  LR_042: "liveroom.errors.notInSession",
  LR_043: "liveroom.errors.participantNotFound",
  LR_044: "liveroom.errors.approvalRequired",
  LR_050: "liveroom.errors.selfKick",
  LR_051: "liveroom.errors.kickCooldown",
  LR_052: "liveroom.errors.micCooldown",
  LR_060: "liveroom.errors.chatEmpty",
  LR_061: "liveroom.errors.chatTooLong",
  LR_070: "liveroom.errors.musicNotOwnSong",
  LR_071: "liveroom.errors.musicNotReady",
  LR_072: "liveroom.errors.musicNotPlaying",
  LR_073: "liveroom.errors.musicInvalidPosition",
  LR_074: "liveroom.errors.musicInvalidVolume",
  LR_075: "liveroom.errors.musicConflict",
  LR_076: "liveroom.errors.musicOwnerAbsent",
  LR_077: "liveroom.errors.musicLoadFailed",
  LR_080: "liveroom.errors.wsUnauthorized",
  LR_081: "liveroom.errors.rateLimited",
  LR_090: "liveroom.errors.rtcSelfSignaling",
  LR_091: "liveroom.errors.rtcPayloadTooLarge",
  LR_092: "liveroom.errors.rtcPayloadInvalid",

  VALIDATION_FAILED: "validation.required",
  VALIDATION_INVALID_REQUEST: "validation.required",
  VALIDATION_CONSTRAINT_VIOLATION: "validation.required",
};

export function resolveErrorI18nKey(error: ApiError | Error | null | undefined): string | null {
  if (!error) return null;
  if (!("code" in error) || !error.code) return null;
  const code = (error as ApiError).code!;

  return EXACT_ERROR_CODE_TO_I18N_KEY[code] ?? null;
}

