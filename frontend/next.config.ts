import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const isDev = process.env.NODE_ENV !== "production";

// `getBackendOrigin` reads the same env var as the axios client, so CSP and
// HTTP traffic always agree on what origin to allow. In dev we additionally
// permit ws/wss so HMR keeps working.
function getBackendOriginForCsp(): string | null {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;
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

const scriptSrc =
  isDev
    ? `script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com`
    : `script-src 'self' 'unsafe-inline' https://accounts.google.com`;

/**
 * Content-Security-Policy. Intentionally permissive in development so HMR
 * and dev-time eval work; production locks it down to known origins only.
 */
const csp = [
  "default-src 'self'",
  scriptSrc,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  `connect-src ${connectSrc}`,
  "frame-src https://accounts.google.com",
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
            value: "same-origin",
          },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
