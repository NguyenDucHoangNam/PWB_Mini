/**
 * Fallback origins used when the NEXT_PUBLIC_* build arguments are missing.
 *
 * These are last resorts, not configuration: a real deployment passes
 * NEXT_PUBLIC_SITE_URL and NEXT_PUBLIC_API_BASE_URL as build arguments
 * (see Frontend/Dockerfile). They exist so that a build made without them
 * still points at the real deployment instead of at `localhost`, which the
 * visitor's browser would resolve to the visitor's own machine.
 *
 * Imported by both `next.config.ts` (to build the Content-Security-Policy)
 * and `constants.ts` (to build the API client base URL). Keeping one copy
 * matters: if the two disagree, the browser makes requests to an origin its
 * own CSP forbids, and the failure surfaces as a blocked request with no
 * obvious cause.
 */
export const PRODUCTION_SITE_URL = "https://producerworkbench.online";

export const PRODUCTION_API_BASE_URL = "https://api.producerworkbench.online/api/v1";

export const DEV_SITE_URL = "http://localhost:3000";

export const DEV_API_BASE_URL = "http://localhost:8080/api/v1";
