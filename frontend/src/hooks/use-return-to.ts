import { useSearchParams, useRouter, usePathname } from "next/navigation";
import { useCallback } from "react";
import { isSafeReturnPath } from "@/lib/config";

const RETURN_TO_KEY = "pwb_return_to";

export function persistReturnTo(returnTo: string): void {
  if (typeof window === "undefined") return;
  if (isSafeReturnPath(returnTo)) {
    sessionStorage.setItem(RETURN_TO_KEY, returnTo);
  }
}

export function readReturnTo(): string | null {
  if (typeof window === "undefined") return null;
  const value = sessionStorage.getItem(RETURN_TO_KEY);
  if (value && isSafeReturnPath(value)) {
    sessionStorage.removeItem(RETURN_TO_KEY);
    return value;
  }
  return null;
}

export function clearReturnTo(): void {
  if (typeof window === "undefined") return;
  sessionStorage.removeItem(RETURN_TO_KEY);
}

/**
 * Hook that captures the `returnTo` query param on the current page and
 * stores it in sessionStorage so it survives the redirect to /login.
 *
 * NOTE: This hook reads `searchParams` during render. That triggers a
 * re-render on every navigation but it's intentional - we want to capture
 * the param as soon as it's available.
 */
export function useCaptureReturnTo(): void {
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  if (typeof window !== "undefined" && returnTo && isSafeReturnPath(returnTo)) {
    sessionStorage.setItem(RETURN_TO_KEY, returnTo);
  }
}

export function useRedirectAfterLogin(): (fallback: string) => void {
  const router = useRouter();
  const pathname = usePathname();
  return useCallback(
    (fallback: string) => {
      const target = readReturnTo();
      router.push(target ?? fallback);
      void pathname;
    },
    [router, pathname],
  );
}
