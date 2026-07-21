import type { ApiError } from "./api-client";

const TECHNICAL_ERROR_PATTERNS = [
  /^Request failed/i,
  /^Network Error/i,
  /^timeout/i,
  /^AxiosError/i,
];

export function isTechnicalMessage(message: string): boolean {
  return TECHNICAL_ERROR_PATTERNS.some((pattern) => pattern.test(message));
}

export function sanitizeApiMessage(err: ApiError | null | undefined, fallbackMessage: string): string {
  if (!err) return fallbackMessage;

  const firstErrorMessage = err.errors?.[0]?.message;
  if (firstErrorMessage && firstErrorMessage.trim().length > 0) {
    return firstErrorMessage;
  }

  if (typeof err.status === "number" && err.status >= 500) {
    return fallbackMessage;
  }

  if (err.message && !isTechnicalMessage(err.message)) {
    return err.message;
  }

  return fallbackMessage;
}

export function resolveApiErrorMessage(
  err: ApiError,
  fallbackMessage: string,
): { field: string | null; message: string } {
  const firstError = err.errors?.[0];
  const message = sanitizeApiMessage(err, fallbackMessage);
  return { field: firstError?.field ?? null, message };
}

export function applyFieldErrors(
  setError: (name: string, error: { message?: string }) => void,
  err: ApiError,
  mapping: Record<string, string>,
  fallbackMessage: string,
  onUnknown: (message: string) => void = () => {},
) {
  const { field, message } = resolveApiErrorMessage(err, fallbackMessage);
  if (field && mapping[field]) {
    setError(mapping[field], { message });
    return;
  }
  onUnknown(message);
}