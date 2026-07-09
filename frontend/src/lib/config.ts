/**
 * Centralized runtime configuration.
 *
 * Why this exists:
 * - Source of truth for the backend base URL is duplicated between the
 *   axios client, the silent refresh helper, the CSP header and several
 *   other places. Each consumer derived it independently, which makes the
 *   app fragile (CSP blocks backend traffic on first run, refresh fails
 *   silently, etc).
 * - A single module lets the CSP and the HTTP client agree on what's
 *   allowed and where to talk to.
 *
 * What lives here:
 * - The backend origin (and only the origin - never the full URL).
 * - A set of public paths where authentication is optional.
 * - Helpers to derive prod-vs-dev behavior from NODE_ENV.
 */

/** Origin (scheme + host + port) of the backend. */
export function getBackendOrigin(): string | null {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!raw) return null;
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

export const isDev = process.env.NODE_ENV !== "production";

/**
 * Paths that never require auth. Used by the axios interceptor to skip
 * the "redirect to /login on 401" behavior.
 *
 * `EXACT_PATHS` are matched against the full pathname only. `PREFIX_PATHS`
 * use `startsWith`. Why two buckets: a naive `startsWith("/")` always
 * matches everything, which would silently disable auth on every route.
 *
 * Adding "/" to EXACT_PATHS keeps the landing page exempt from
 * redirect-on-401 without accidentally making the whole app public.
 */
export const PUBLIC_EXACT_PATHS: ReadonlySet<string> = new Set(["/"]);

export const PUBLIC_PREFIX_PATHS: readonly string[] = [
  "/login",
  "/register",
  "/verify-otp",
  "/forgot-password",
  "/reset-password",
  "/account-recovery",
  "/401",
  "/403",
];

export function isPublicPath(pathname: string): boolean {
  if (!pathname) return false;
  if (PUBLIC_EXACT_PATHS.has(pathname)) return true;
  // Match either:
  //   - the prefix exactly (e.g. "/login"), or
  //   - the prefix followed by "/" (e.g. "/login/something"),
  // so "/account-recovery-real" doesn't accidentally match "/account-recovery".
  return PUBLIC_PREFIX_PATHS.some(
    (p) => pathname === p || pathname.startsWith(`${p}/`),
  );
}

/**
 * Same-origin (or relative) absolute path used for `returnTo`.
 * Excludes protocol-relative URLs and external URLs (open-redirect guard).
 */
export function isSafeReturnPath(path: string): boolean {
  if (!path) return false;
  if (!path.startsWith("/")) return false;
  if (path.startsWith("//")) return false;
  return true;
}
