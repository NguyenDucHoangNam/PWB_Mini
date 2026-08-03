import type { ApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";

type Translator = (key: string) => string;

const VOICE_ERRORS_PREFIX = "voice.errors.";

function extractValidationDetails(error: ApiError): string | null {
  const fieldErrors = error.fieldErrors;
  if (!fieldErrors || typeof fieldErrors !== "object") return null;

  const messages: string[] = [];
  for (const [field, fieldMessages] of Object.entries(fieldErrors)) {
    if (Array.isArray(fieldMessages)) {
      for (const msg of fieldMessages) {
        if (/[\u00C0-\u024F\u1EA0-\u1EF9]/.test(msg) || (/^[A-Z]/.test(msg) && msg.includes(" "))) {
          messages.push(msg);
        } else {
          messages.push(`${field}: ${msg}`);
        }
      }
    }
  }
  return messages.length > 0 ? messages.join("\n") : null;
}

export function resolveVoiceErrorMessage(
  error: unknown,
  tErrors: Translator,
  tCommon: Translator,
): string {
  if (error instanceof Error && "code" in error && (error as ApiError).code) {
    const apiErr = error as ApiError;

    if (apiErr.code === "VALIDATION_FAILED") {
      const details = extractValidationDetails(apiErr);
      if (details) return details;
    }

    const key = resolveErrorI18nKey(apiErr);
    if (key && key.startsWith(VOICE_ERRORS_PREFIX)) {
      const shortKey = key.slice(VOICE_ERRORS_PREFIX.length);
      return tErrors(shortKey);
    }
  }

  const fallback = (error as ApiError | null | undefined)?.message;
  if (fallback && fallback.trim()) return fallback;

  return tCommon("error");
}
