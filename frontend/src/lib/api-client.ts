import axios, { type AxiosRequestConfig } from "axios";
import type { ApiResponse } from "@/types/api";
import { API_BASE_URL } from "@/lib/constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "./auth-refresh";
import { isPublicPath } from "./config";

function getCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return decodeURIComponent(parts.pop()?.split(";").shift() || "");
  return null;
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
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  const locale = getCookie("locale") || "vi";
  config.headers["Accept-Language"] = locale;

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

    // On a protected page: try the silent refresh once. If it succeeds,
    // replay the original request with the new token.
    if (isUnauthorized && !onPublicPage && !alreadyRetried) {
      retriedRequests.set(originalRequest, true);
      try {
        const newAccessToken = await refreshAccessToken();
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh failed - the session is dead. Drop credentials and
        // bounce the user back to /login. Skipped on public paths so a
        // guest can see the landing page without being chased away.
        useAuthStore.getState().clearAuth();
        if (typeof window !== "undefined") {
          const returnTo = encodeURIComponent(
            window.location.pathname + window.location.search,
          );
          window.location.href = `/login?returnTo=${returnTo}`;
        }
        return Promise.reject(refreshError);
      }
    }

    // Either we've already retried, or the 401 came from a public page.
    // Public pages (including "/") must NEVER redirect on 401, otherwise
    // an unauthenticated visitor lands on /login immediately. Just clear
    // stale credentials and let the caller decide what to do.
    if (isUnauthorized) {
      useAuthStore.getState().clearAuth();
      if (typeof window !== "undefined" && !onPublicPage) {
        const returnTo = encodeURIComponent(
          window.location.pathname + window.location.search,
        );
        window.location.href = `/login?returnTo=${returnTo}`;
      }
    }

    const apiError = error.response.data as ApiResponse<unknown>;
    return Promise.reject(apiError);
  },
);

export { apiClient, isPublicPath };
