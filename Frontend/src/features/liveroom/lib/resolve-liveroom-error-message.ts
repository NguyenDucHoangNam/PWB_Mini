import type { ApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";

type Translator = (key: string, values?: Record<string, string | number>) => string;

const LIVEROOM_ERRORS_PREFIX = "liveroom.errors.";

export function asApiErrorLike(error: unknown): ApiError | null {
  if (error instanceof Error && "code" in error && (error as ApiError).code) {
    return error as ApiError;
  }
  return null;
}

export function liveroomErrorCodeOf(error: unknown): string | null {
  return asApiErrorLike(error)?.code ?? null;
}

export function resolveLiveroomErrorMessage(
  error: unknown,
  tErrors: Translator,
  tCommon: Translator,
): string {
  const apiErr = asApiErrorLike(error);

  if (apiErr) {
    const key = resolveErrorI18nKey(apiErr);
    if (key && key.startsWith(LIVEROOM_ERRORS_PREFIX)) {
      const shortKey = key.slice(LIVEROOM_ERRORS_PREFIX.length);
      const seconds = apiErr.retryAfterSeconds;
      return tErrors(shortKey, seconds != null ? { seconds } : undefined);
    }
  }

  const fallback = (error as ApiError | null | undefined)?.message;
  if (fallback && fallback.trim()) return fallback;

  return tCommon("error");
}