import type { ApiError } from "@/lib/api-client";

// The audio module's codes. They were `VOICE_0xx` until the module was renamed to `audio`; the entries
// here kept the old names long afterwards, so not one of the 27 codes the backend actually sends matched
// anything. Nothing broke visibly — `resolveVoiceErrorMessage` falls through to the server's own
// localized message — which is exactly why it went unnoticed: every translation below was dead, and the
// text users saw came from `Backend/modules/audio/src/main/resources/audio/messages*.properties`.
//
// Several codes deliberately share one key. A user cannot act differently on "FFmpeg produced empty
// output" than on "processing failed", and telling them which internal step broke is noise; the
// distinction stays in the server logs, where it belongs.
const EXACT_ERROR_CODE_TO_I18N_KEY: Record<string, string> = {
  AUDIO_001: "voice.errors.songNotFound",
  AUDIO_002: "voice.errors.voiceTagNotFound",
  AUDIO_003: "voice.errors.genericError",
  AUDIO_004: "voice.errors.unsupportedFormat",
  AUDIO_005: "voice.errors.fileSizeExceeded",
  AUDIO_006: "voice.errors.duplicateVoiceTagName",
  AUDIO_007: "voice.errors.voiceTagInUse",
  AUDIO_008: "voice.errors.streamNotReady",
  AUDIO_010: "voice.errors.storageError",
  AUDIO_011: "voice.errors.ttsFailed",
  AUDIO_012: "voice.errors.unauthorizedAccess",
  AUDIO_013: "voice.errors.processingFailed",
  AUDIO_014: "voice.errors.invalidAudioFile",
  AUDIO_015: "voice.errors.invalidAudioFile",
  AUDIO_016: "voice.errors.ttsUnavailable",
  AUDIO_017: "voice.errors.textRequired",
  AUDIO_018: "voice.errors.fileEmpty",
  AUDIO_019: "voice.errors.unsupportedFormat",
  AUDIO_020: "voice.errors.genericError",
  AUDIO_021: "voice.errors.processingFailed",
  AUDIO_022: "voice.errors.intervalShorterThanTag",
  AUDIO_023: "voice.errors.processingFailed",
  AUDIO_024: "voice.errors.uploadNotFound",
  AUDIO_025: "voice.errors.ttsVoiceNotSupported",
  AUDIO_026: "voice.errors.retryNotAllowed",
  AUDIO_027: "voice.errors.voiceTagTooLong",
  AUDIO_028: "voice.errors.processingTimedOut",
  AUDIO_029: "voice.errors.uploadAlreadyRegistered",

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
  LR_062: "liveroom.errors.commentEmpty",
  LR_063: "liveroom.errors.commentTooLong",
  LR_064: "liveroom.errors.commentSongMismatch",
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

