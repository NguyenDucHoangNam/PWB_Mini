import type { ApiError } from "./api-client";

export function resolveApiErrorMessage(
  err: ApiError,
  fallbackMessage: string,
): { field: string | null; message: string } {
  const firstError = err.errors?.[0];
  const message = firstError?.message ?? err.message ?? fallbackMessage;
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