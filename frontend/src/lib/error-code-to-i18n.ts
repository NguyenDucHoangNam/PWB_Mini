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

  VALIDATION_FAILED: "validation.required",
  VALIDATION_INVALID_REQUEST: "validation.required",
  VALIDATION_CONSTRAINT_VIOLATION: "validation.required",
  DATA_INTEGRITY_VIOLATION: "errors.common.dataIntegrity",
  COMMON_OPERATION_FAILED: "errors.common.operationFailed",
  RATE_LIMITED: "errors.common.rateLimited",
  RATE_LIMITED_MESSAGE: "errors.common.rateLimited",
  FILE_TOO_LARGE: "errors.common.fileTooLarge",
  RESOURCE_NOT_FOUND: "errors.common.notFound",
  INTERNAL_SERVER_ERROR: "errors.common.serverError",

  IAM_001: "errors.auth.emailAlreadyRegistered",
  IAM_002: "errors.auth.weakPassword",
  IAM_003: "errors.auth.userNotFound",
  IAM_004: "errors.auth.invalidCredentials",
  IAM_005: "errors.auth.accountLocked",
  IAM_006: "errors.auth.accountInactive",
  IAM_007: "errors.auth.accountNotVerified",
  IAM_008: "errors.auth.tokenInvalid",
  IAM_009: "errors.auth.tokenExpired",
  IAM_010: "errors.auth.tokenMissing",
  IAM_011: "errors.auth.refreshTokenInvalid",
  IAM_012: "errors.auth.refreshTokenExpired",
  IAM_013: "errors.auth.refreshTokenReused",
  IAM_014: "errors.auth.rateLimited",
  IAM_015: "errors.auth.currentPasswordIncorrect",
  IAM_016: "errors.auth.passwordReused",
  IAM_017: "errors.auth.resetCooldown",
  IAM_018: "errors.auth.defaultRoleMissing",
  IAM_021: "errors.auth.otpInvalid",
  IAM_022: "errors.auth.otpExpired",
  IAM_026: "errors.auth.rateLimited",
  IAM_027: "errors.auth.otpDailyLimit",
  IAM_030: "errors.auth.passwordRecentlyUsed",
  IAM_031: "errors.auth.resetTokenInvalid",
  IAM_033: "errors.auth.oauthNoPassword",
  IAM_034: "errors.auth.serviceUnavailable",
  IAM_ACCESS_001: "errors.auth.accessDenied",
  IAM_GOOGLE_001: "errors.auth.googleTokenInvalid",
  IAM_GOOGLE_002: "errors.auth.googleEmailNotVerified",
  IAM_PROFILE_001: "errors.profile.avatarInvalidFormat",
  IAM_PROFILE_002: "errors.profile.avatarTooLarge",
};

const PREFIX_ERROR_CODE_TO_I18N_KEY: Array<{ prefix: string; key: string }> = [
  { prefix: "IAM_", key: "errors.auth.generic" },
  { prefix: "AUTH_", key: "errors.auth.generic" },
];

export function resolveErrorI18nKey(error: ApiError | Error | null | undefined): string | null {
  if (!error) return null;
  if (!("code" in error) || !error.code) return null;
  const code = (error as ApiError).code!;

  const exact = EXACT_ERROR_CODE_TO_I18N_KEY[code];
  if (exact) return exact;

  for (const { prefix, key } of PREFIX_ERROR_CODE_TO_I18N_KEY) {
    if (code.startsWith(prefix)) return key;
  }
  return null;
}
