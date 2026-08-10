import { DEV_API_BASE_URL, PRODUCTION_API_BASE_URL } from "./site-defaults";

// The value is inlined at build time, so the fallback is what a build made without
// NEXT_PUBLIC_API_BASE_URL ships to every visitor. A localhost fallback there would send the
// browser to the visitor's own machine, which fails in a way that looks like the backend is down.
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  (process.env.NODE_ENV === "production" ? PRODUCTION_API_BASE_URL : DEV_API_BASE_URL);

export const DEFAULT_STALE_TIME = 5 * 60 * 1000;

export const DEFAULT_PAGE_SIZE = 10;
