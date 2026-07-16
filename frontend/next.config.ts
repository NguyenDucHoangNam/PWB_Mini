import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const isDev = process.env.NODE_ENV !== "production";

// `getBackendOrigin` reads the same env var as the axios client, so CSP and
// HTTP traffic always agree on what origin to allow. The dev fallback below
// mirrors constants.ts; without it, the browser blocks the first request
// before any auth can run.
function getBackendOriginForCsp(): string | null {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL ?? (isDev ? "http://localhost:8080/api/v1" : "");
  if (!raw) return null;
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

const backendOrigin = getBackendOriginForCsp();
const sameOrigin =
  process.env.NEXT_PUBLIC_SITE_URL ??
  (isDev ? "http://localhost:3000" : "https://pwb-mini.example.com");

/**
 * Build the connect-src directive based on the configured backend origin.
 * - Always: 'self' (same-origin), https://accounts.google.com (Google Identity).
 * - Dev: ws:/wss: (Next.js HMR websocket).
 * - When `NEXT_PUBLIC_API_BASE_URL` is set: include its origin so the
 *   browser allows `fetch`/XHR to the backend. Without this, the first
 *   request after page load is blocked by CSP before any auth can run.
 */
const connectSrc = [
  "'self'",
  sameOrigin,
  backendOrigin,
  "https://accounts.google.com",
  isDev ? "ws: wss:" : null,
]
  .filter((value): value is string => Boolean(value))
  .join(" ");

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
 *   and `NEXT_PUBLIC_STORAGE_REGION`, so dev can hit a local MinIO or
 *   real AWS without extra config.
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
  `media-src 'self'${storageOrigins.length ? " " + storageOrigins.join(" ") : ""}`,
  "frame-src https://accounts.google.com https://challenges.cloudflare.com",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const nextConfig: NextConfig = {
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
            value: "microphone=(self), camera=(), geolocation=()",
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
