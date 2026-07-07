import axios from "axios";
import type { ApiResponse } from "@/types/api";
import { API_BASE_URL } from "@/lib/constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "./auth-refresh";

// Helper to get cookie on browser
function getCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop()?.split(";").shift() || null;
  return null;
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  // Set Accept-Language header for backend i18n
  const locale = getCookie("locale") || "vi";
  config.headers["Accept-Language"] = locale;

  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (axios.isAxiosError(error) && error.response) {
      const isUnauthorized = error.response.status === 401;
      const isLoginOrErrorPage = typeof window !== "undefined" && (
        window.location.pathname.startsWith("/401") ||
        window.location.pathname.startsWith("/login")
      );

      // Attempt silent refresh if 401 and we are not on login/error pages
      if (isUnauthorized && !isLoginOrErrorPage && !originalRequest._retry) {
        originalRequest._retry = true;

        try {
          const newAccessToken = await refreshAccessToken();
          // Update authorization header and retry request
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          return apiClient(originalRequest);
        } catch (refreshError) {
          useAuthStore.getState().clearAuth();
          if (typeof window !== "undefined") {
            window.location.href = "/401";
          }
          return Promise.reject(refreshError);
        }
      }

      // If already retried or unauthorized on login page, just clear and reject
      if (isUnauthorized) {
        useAuthStore.getState().clearAuth();
        if (typeof window !== "undefined" && !isLoginOrErrorPage) {
          window.location.href = "/401";
        }
      }

      const apiError = error.response.data as ApiResponse<unknown>;
      return Promise.reject(apiError);
    }

    return Promise.reject(error);
  },
);

export { apiClient };
