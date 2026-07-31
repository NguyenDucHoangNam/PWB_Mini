import axios from "axios";
import { API_BASE_URL } from "./constants";
import { useAuthStore, type AuthUser } from "@/features/auth/stores/use-auth-store";
import { decodeJwtExpiry } from "./jwt-decode";
import type { ApiResponse } from "@/types/api";
import type { AuthResponse } from "@/features/auth/types";
import { broadcastAuthMessage, AUTH_CHANNEL } from "./broadcast-channel";
import { mapAuthResponseToUser } from "@/features/auth/lib/map-auth-response";

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
    const response = await axios.post<ApiResponse<AuthResponse>>(
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

    const data = response.data.data;
    const expiresAt = decodeJwtExpiry(data.accessToken);
    const existingUser = useAuthStore.getState().user;

    let user: AuthUser;
    if (existingUser) {
      user = existingUser;
    } else {
      user = mapAuthResponseToUser(data, { oauthProvider: "LOCAL" });
    }

    if (existingUser && data.avatarUrl !== undefined && existingUser.avatarUrl !== data.avatarUrl) {
      useAuthStore.getState().setAvatarUrl(data.avatarUrl ?? null);
    }

    useAuthStore.getState().setAuth(data.accessToken, user, expiresAt ?? undefined);
    broadcastAuthMessage({ type: "TOKEN_UPDATED", token: data.accessToken, user });

    processQueue(null, data.accessToken);
    return data.accessToken;
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

export { broadcastAuthMessage };
void AUTH_CHANNEL;
