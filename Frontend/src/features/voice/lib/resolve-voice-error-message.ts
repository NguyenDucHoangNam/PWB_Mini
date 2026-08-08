import type { ApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";

type Translator = (key: string) => string;

const VOICE_ERRORS_PREFIX = "voice.errors.";

/**
 * What the backend answers when the PRO guard on the audio controllers refuses a request.
 *
 * <p>Handled here rather than in the shared code map because the code itself is generic — it is the same
 * one an ADMIN-only endpoint returns — and mapping it globally to a voice message would mislabel every
 * other refusal in the application. Inside this module a 403 has exactly one cause: the routes that
 * create audio require a subscription, and every one of them is reached from a screen the user opened
 * expecting it to work. Telling them to upgrade is more useful than "access denied".
 */
const ACCESS_DENIED_CODE = "IAM_ACCESS_001";

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

    if (apiErr.code === ACCESS_DENIED_CODE) {
      return tErrors("proOnly");
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
