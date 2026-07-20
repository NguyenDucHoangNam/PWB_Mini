import type { ApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";

type Translator = (key: string) => string;

export function resolveVoiceErrorMessage(
  error: unknown,
  tErrors: Translator,
  tCommon: Translator,
): string {
  if (error instanceof Error && "code" in error && (error as ApiError).code) {
    const key = resolveErrorI18nKey(error as ApiError);
    if (key) return tErrors(key);
  }

  const fallback = (error as ApiError | null | undefined)?.message;
  if (fallback && fallback.trim()) return fallback;

  return tCommon("error");
}
