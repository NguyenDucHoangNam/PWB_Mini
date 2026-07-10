import axios from "axios";
import { API_BASE_URL } from "./constants";
import { useAuthStore, type AuthUser } from "@/features/auth/stores/use-auth-store";
import { decodeJwtExpiry } from "./jwt-decode";
import type { ApiResponse } from "@/types/api";
import type { RefreshResponse } from "@/features/auth/types";
import { AUTH_CHANNEL, broadcastAuthMessage } from "./broadcast-channel";

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];
let activeRefreshController: AbortController | null = null;

const processQueue = (error: unknown, token: string | null = null) => {
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
    // Refresh relies solely on the httpOnly refresh cookie. Do NOT send
    // Authorization header here - sending an empty Bearer token is a
    // common bug and leaks a partial header to the server.
    const response = await axios.post<ApiResponse<RefreshResponse>>(
      `${API_BASE_URL}/auth/refresh`,
      {},
      {
        withCredentials: true,
        signal: activeRefreshController.signal,
      },
    );

    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || "Failed to refresh token");
    }

    const { accessToken } = response.data.data;
    const expiresAt = decodeJwtExpiry(accessToken);
    let user: AuthUser | null = useAuthStore.getState().user;

    // If we don't have a user in memory yet (e.g. fresh tab refresh), fetch
    // the profile so other tabs and the rest of the app have valid data.
    if (!user) {
      try {
        const profileResponse = await axios.get<ApiResponse<AuthUser>>(`${API_BASE_URL}/auth/me`, {
          headers: { Authorization: `Bearer ${accessToken}` },
          withCredentials: true,
        });
        if (profileResponse.data.success && profileResponse.data.data) {
          user = profileResponse.data.data;
        }
      } catch {
        // If profile fetch fails we still keep the new token; downstream
        // requests will surface the error.
      }
    }

    if (user) {
      useAuthStore.getState().setAuth(accessToken, user, expiresAt ?? undefined);
    } else {
      useAuthStore.setState({ accessToken, accessTokenExpiresAt: expiresAt });
    }

    // Cross-tab sync - propagate the new token so other tabs can update.
    if (user) {
      broadcastAuthMessage({ type: "TOKEN_UPDATED", token: accessToken, user });
    }

    processQueue(null, accessToken);
    return accessToken;
  } catch (error: unknown) {
    if (
      axios.isAxiosError(error) &&
      (error.name === "CanceledError" || error.code === "ERR_CANCELED")
    ) {
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
