import axios from "axios";
import { API_BASE_URL } from "./constants";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import type { ApiResponse } from "@/types/api";
import type { RefreshResponse } from "@/features/auth/types";

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: any) => void;
}> = [];
let activeRefreshController: AbortController | null = null;

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

export const abortRefresh = () => {
  if (activeRefreshController) {
    activeRefreshController.abort();
    activeRefreshController = null;
  }
  // Reset state
  isRefreshing = false;
  failedQueue = [];
};

export const refreshAccessToken = async (): Promise<string> => {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;
  activeRefreshController = new AbortController();

  try {
    const expiredToken = useAuthStore.getState().accessToken;
    // Call refresh API with expired access token in auth header and refresh token in cookie
    const response = await axios.post<ApiResponse<RefreshResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      {},
      {
        headers: {
          Authorization: `Bearer ${expiredToken || ""}`,
        },
        withCredentials: true, // Send httpOnly cookie
        signal: activeRefreshController.signal,
      }
    );

    if (response.data.success && response.data.data) {
      const { accessToken } = response.data.data;
      const currentUser = useAuthStore.getState().user;

      if (currentUser) {
        // Save new token to store (this will trigger tab sync)
        useAuthStore.getState().setAuth(accessToken, currentUser);
      }

      processQueue(null, accessToken);
      return accessToken;
    } else {
      throw new Error(response.data.message || "Failed to refresh token");
    }
  } catch (error: any) {
    // Ignore abort errors
    if (error.name === "AbortError" || error.code === "ERR_CANCELED") {
      const abortError = new Error("Refresh aborted");
      abortError.name = "AbortError";
      processQueue(abortError, null);
      throw abortError;
    }
    processQueue(error, null);
    useAuthStore.getState().clearAuth();
    throw error;
  } finally {
    isRefreshing = false;
    activeRefreshController = null;
  }
};
