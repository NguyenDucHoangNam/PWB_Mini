import axios, {
  AxiosHeaders,
  type AxiosProgressEvent,
  type AxiosRequestConfig,
} from "axios";
import type { ApiResponse } from "@/types/api";
import { API_BASE_URL } from "@/lib/constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "./auth-refresh";
import { isPublicPath } from "./config";
import { SKIP_REFRESH_HEADER, shouldSkipRefresh } from "./request-flags";

function toPositiveSeconds(value: unknown): number | undefined {
  if (value === undefined || value === null || value === "") return undefined;
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return Math.ceil(numeric);
  }
  return undefined;
}

function parseRetryAfterHeader(headers: Record<string, string>): number | undefined {
  const candidates = ["retry-after", "ratelimit-reset", "x-ratelimit-reset"];
  for (const key of candidates) {
    const seconds = toPositiveSeconds(headers[key] ?? headers[key.toUpperCase()]);
    if (seconds !== undefined) return seconds;
  }
  return undefined;
}

/**
 * The same wait, read out of the response body.
 *
 * Headers are the primary source but not a reliable one from a browser: they only reach JavaScript
 * when the API lists them in `Access-Control-Expose-Headers`, and any proxy in between is free to
 * drop them. The backend therefore also puts `retryAfterSeconds` in the error payload of every
 * throttled response, and that copy is what keeps the countdown working when the header does not
 * survive the trip.
 */
function parseRetryAfterBody(data: unknown): number | undefined {
  if (!data || typeof data !== "object") return undefined;
  const details = (data as { error?: unknown }).error;
  if (!details || typeof details !== "object") return undefined;
  return toPositiveSeconds((details as Record<string, unknown>).retryAfterSeconds);
}

function getCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return decodeURIComponent(parts.pop()?.split(";").shift() || "");
  return null;
}

function ensureHeaders(config: AxiosRequestConfig): AxiosHeaders {
  if (config.headers instanceof AxiosHeaders) {
    return config.headers;
  }
  const next = new AxiosHeaders();
  if (config.headers) {
    for (const [key, value] of Object.entries(config.headers)) {
      if (value !== undefined) {
        next.set(key, value as string | number | boolean);
      }
    }
  }
  config.headers = next;
  return next;
}

/**
 * How long a file upload may take before the client gives up. Generous on purpose: the cost of being
 * too patient is a spinner, while the cost of being too eager is reporting a failure for work the
 * server actually completed.
 */
const UPLOAD_TIMEOUT_MS = 5 * 60 * 1000;

/** Reports upload progress as a whole percentage. */
export type UploadProgressHandler = (percent: number) => void;

/**
 * Adapts axios's byte counts for an {@link UploadProgressHandler}. A request whose total size the
 * browser cannot determine reports nothing rather than a made-up number.
 */
export function onUploadProgress(handler: UploadProgressHandler | undefined) {
  if (!handler) return undefined;
  return (event: AxiosProgressEvent) => {
    if (!event.total) return;
    handler(Math.min(100, Math.round((event.loaded * 100) / event.total)));
  };
}

export class ApiError<T = unknown> extends Error {
  status: number;
  headers: Record<string, string>;
  success: boolean;
  data: T | null;
  errors?: Array<{ code: string; message: string; field?: string | null }>;
  fieldErrors?: Record<string, string[]>;
  timestamp?: string;
  traceId?: string | null;
  code?: string;
  retryAfterSeconds?: number;

  constructor(params: {
    status: number;
    headers: Record<string, string>;
    success?: boolean;
    data?: T | null;
    errors?: Array<{ code: string; message: string; field?: string | null }> | null;
    error?: Record<string, string[]> | null;
    timestamp?: string;
    traceId?: string | null;
    message?: string;
    code?: string;
    retryAfterSeconds?: number;
  }) {
    super(params.message ?? params.errors?.[0]?.message ?? "API Error");
    this.name = "ApiError";
    this.status = params.status;
    this.headers = params.headers;
    this.success = params.success ?? false;
    this.data = params.data ?? null;
    if (params.errors) this.errors = params.errors;
    if (params.error && typeof params.error === "object") this.fieldErrors = params.error;
    if (params.timestamp) this.timestamp = params.timestamp;
    if (params.traceId) this.traceId = params.traceId;
    this.code = params.code ?? params.errors?.[0]?.code;
    if (typeof params.retryAfterSeconds === "number" && params.retryAfterSeconds > 0) {
      this.retryAfterSeconds = params.retryAfterSeconds;
    }
  }
}

/**
 * Use this type for TanStack Query `onError` callbacks that receive an
 * {@link ApiError}. The runtime contract from our Axios interceptor always
 * rejects with an `ApiError` instance, but the Query type system expects a
 * generic `Error`. The cast helper preserves the narrow type at the call site
 * without the boilerplate of repeating the cast in every component.
 */
export type ApiMutationOnError<TData = unknown> = (err: ApiError<TData>) => void;

export function asApiError<TData = unknown>(handler: ApiMutationOnError<TData>) {
  return handler as unknown as (err: Error) => void;
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 15000,
  withCredentials: true,
});

// Per-request retry marker. Reusing the same WeakMap entry between
// requests is intentional - it lives only as long as the original
// AxiosRequestConfig.
const retriedRequests = new WeakMap<AxiosRequestConfig, boolean>();

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  const headers = ensureHeaders(config);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  headers.set("Accept-Language", getCookie("locale") || "vi");
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    // The client-wide JSON content type is wrong for a multipart body, and leaving it in place does
    // more than mislabel the request: axios serialises FormData to JSON when it sees a JSON content
    // type, so the file would silently never be sent. Dropping it lets the browser set the boundary.
    headers.delete("Content-Type");

    // The default timeout is sized for JSON calls. A file upload is not one: it carries megabytes over
    // the user's uplink and the server then forwards it to object storage. Leaving the short timeout in
    // place aborts the request client-side while the server goes on to finish successfully — the user
    // is told it failed and then watches the change take effect anyway.
    config.timeout = UPLOAD_TIMEOUT_MS;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!axios.isAxiosError(error) || !error.response || !error.config) {
      return Promise.reject(error);
    }

    const originalRequest = error.config as AxiosRequestConfig;
    const isUnauthorized = error.response.status === 401;
    const pathname = typeof window !== "undefined" ? window.location.pathname : "";
    const onPublicPage = isPublicPath(pathname);
    const alreadyRetried = retriedRequests.has(originalRequest);
    const callerSkippedRefresh = shouldSkipRefresh(originalRequest);

    if (
      isUnauthorized &&
      !onPublicPage &&
      !alreadyRetried &&
      !callerSkippedRefresh
    ) {
      retriedRequests.set(originalRequest, true);
      try {
        const newAccessToken = await refreshAccessToken();
        const headers = ensureHeaders(originalRequest);
        headers.set("Authorization", `Bearer ${newAccessToken}`);
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh failed - the session is dead. Drop credentials and
        // bounce the user back to /login. Skipped on public paths so a
        // guest can see the landing page without being chased away.
        useAuthStore.getState().clearAuth();
        if (typeof window !== "undefined") {
          const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
          window.location.href = `/login?returnTo=${returnTo}`;
        }
        return Promise.reject(refreshError);
      }
    }

    if (isUnauthorized) {
      useAuthStore.getState().clearAuth();
      if (typeof window !== "undefined" && !onPublicPage) {
        const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login?returnTo=${returnTo}`;
      }
    }

    const rawHeaders = Object.fromEntries(
      Object.entries(error.response.headers).map(([k, v]) => [k, String(v)]),
    );
    const retryAfterSeconds =
      parseRetryAfterHeader(rawHeaders) ?? parseRetryAfterBody(error.response.data);

    const apiError = new ApiError({
      ...(error.response.data as ApiResponse<unknown>),
      status: error.response.status,
      headers: rawHeaders,
      retryAfterSeconds,
    });
    if (!originalRequest[SKIP_REFRESH_HEADER]) {
      originalRequest[SKIP_REFRESH_HEADER] = true;
    }
    return Promise.reject(apiError);
  },
);

export { apiClient, isPublicPath, SKIP_REFRESH_HEADER };
