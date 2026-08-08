import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

import {
  DEV_API_BASE_URL,
  PRODUCTION_API_BASE_URL,
  PRODUCTION_SITE_URL,
} from "./src/lib/site-defaults";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const isDev = process.env.NODE_ENV !== "production";

// `getBackendOrigin` reads the same env var as the axios client and falls back to the same
// defaults, so CSP and HTTP traffic always agree on what origin to allow. When they disagree the
// browser blocks a request the application is certain it is allowed to make.
function getBackendOriginForCsp(): string | null {
  const raw =
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    (isDev ? DEV_API_BASE_URL : PRODUCTION_API_BASE_URL);
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

const backendOrigin = getBackendOriginForCsp();
const sameOrigin =
  process.env.NEXT_PUBLIC_SITE_URL ?? (isDev ? "http://localhost:3000" : PRODUCTION_SITE_URL);

/**
 * Lets a developer point a production build at a local backend without hitting
 * a confusing CSP violation.
 *
 * Scoped to development on purpose. This used to be unconditional, on the
 * reasoning that a deployed page never calls localhost so allowing it is
 * harmless — but `connect-src` is a limit on what injected script may reach,
 * not a prediction of what our own code does. An attacker with a script
 * foothold could probe services on the visitor's own machine, and the entry
 * buys production nothing in return.
 */
const localBackendFallback = isDev ? "http://localhost:8080" : null;

/**
 * The live room talks STOMP over a WebSocket to the backend origin, so that
 * origin has to be allowed with a ws(s):// scheme too — `connect-src` matches
 * on scheme, and the http(s) entry above does not cover the handshake.
 */
const backendWsOrigin = backendOrigin ? backendOrigin.replace(/^http/, "ws") : null;
const localBackendWsFallback = localBackendFallback
  ? localBackendFallback.replace(/^http/, "ws")
  : null;

/**
 * Build the connect-src directive based on the configured backend origin.
 * - Always: 'self' (same-origin), https://accounts.google.com (Google Identity),
 *   and the backend's ws(s) origin for the live room socket.
 * - Dev: ws:/wss: (Next.js HMR websocket).
 * - When `NEXT_PUBLIC_API_BASE_URL` is set: include its origin so the
 *   browser allows `fetch`/XHR to the backend. Without this, the first
 *   request after page load is blocked by CSP before any auth can run.
 */
const connectSrc = Array.from(
  // Deduplicated: in development the configured backend origin and the local fallback are the same
  // string, and listing an origin twice makes the header harder to read while changing nothing.
  new Set(
    [
      "'self'",
      sameOrigin,
      backendOrigin,
      localBackendFallback,
      backendWsOrigin,
      localBackendWsFallback,
      "https://accounts.google.com",
      isDev ? "ws: wss:" : null,
    ].filter((value): value is string => Boolean(value)),
  ),
).join(" ");

/**
 * SCRIPT-SRC POLICY
 *
 * `'unsafe-inline'` is currently retained because Next.js 16 (with Turbopack)
 * bootstraps the app via inline `<script>` tags before our handler can add a
 * per-request nonce. Removing this requires:
 *   1. A nonced runtime config (`generateNonceForHeader` + custom server), AND
 *   2. Adopting the App Router streaming RSC nonce generator.
 *
 * Until both land together, swapping the policy to `'strict-dynamic'` plus a
 * nonce breaks hydration in production. Track this as a follow-up.
 */
const scriptSrc =
  isDev
    ? `script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com https://challenges.cloudflare.com`
    : `script-src 'self' 'unsafe-inline' https://accounts.google.com https://challenges.cloudflare.com`;

/**
 * Storage origins allowed for audio `<media>` playback and presigned fetches.
 *
 * `media-src` is REQUIRED whenever the app plays files from S3 — without it
 * the browser falls back to `default-src 'self'` and blocks the request.
 *
 * Sources:
 * - `NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX` (preferred in prod — usually a
 *   CloudFront/CDN domain).
 * - The S3 regional bucket URL built from `NEXT_PUBLIC_STORAGE_BUCKET_NAME`
 *   and `NEXT_PUBLIC_STORAGE_REGION`. Every environment talks to real AWS S3,
 *   so these two are enough; `NEXT_PUBLIC_STORAGE_ENDPOINT` stays only as an
 *   escape hatch for an S3-compatible gateway.
 *
 * Public prefixes that look host-like (contain a dot) are kept; otherwise we
 * skip them to avoid polluting the directive with `https:` or garbage.
 */
function getStorageOrigins(): string[] {
  const allowed = new Set<string>();
  const collect = (value: string | undefined) => {
    if (!value) return;
    try {
      allowed.add(new URL(value).origin);
    } catch {
      // ignore malformed URLs
    }
  };

  collect(process.env.NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX);

  const bucket = process.env.NEXT_PUBLIC_STORAGE_BUCKET_NAME;
  const region = process.env.NEXT_PUBLIC_STORAGE_REGION;
  if (bucket && region) {
    collect(`https://${bucket}.s3.${region}.amazonaws.com`);
    collect(`https://s3.${region}.amazonaws.com/${bucket}`);
  }

  const endpoint = process.env.NEXT_PUBLIC_STORAGE_ENDPOINT;
  if (endpoint) {
    try {
      const origin = new URL(endpoint).origin;
      if (origin) allowed.add(origin);
    } catch {
      // ignore malformed URLs
    }
  }

  return Array.from(allowed);
}

const storageOrigins = getStorageOrigins();

/**
 * Content-Security-Policy. Intentionally permissive in development so HMR
 * and dev-time eval work; production locks it down to known origins only.
 */
const csp = [
  "default-src 'self'",
  scriptSrc,
  "style-src 'self' 'unsafe-inline' https://accounts.google.com",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src ${connectSrc}${storageOrigins.length ? " " + storageOrigins.join(" ") : ""}`,
  // `blob:` is required by the TTS preview: nothing is stored server-side, so the response arrives as
  // raw audio the client turns into an object URL. `'self'` does not cover blob: URLs.
  `media-src 'self' blob:${storageOrigins.length ? " " + storageOrigins.join(" ") : ""}`,
  "frame-src https://accounts.google.com https://challenges.cloudflare.com",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const nextConfig: NextConfig = {
  /**
   * Emits a self-contained server bundle with only the dependencies actually reached at runtime,
   * which is what the production image copies. Without it the image has to carry the whole
   * `node_modules` tree — roughly 1GB instead of 200MB.
   */
  output: "standalone",

  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          {
            key: "Strict-Transport-Security",
            value: "max-age=63072000; includeSubDomains; preload",
          },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          {
            key: "Permissions-Policy",
            value: "microphone=(self), camera=(self), geolocation=()",
          },
          {
            key: "Cross-Origin-Opener-Policy",
            value: "same-origin-allow-popups",
          },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
