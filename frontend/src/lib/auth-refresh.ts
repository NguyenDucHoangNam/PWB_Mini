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

export const refreshAccessToken = async (): Promise<string> => {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;

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
    processQueue(error, null);
    useAuthStore.getState().clearAuth();
    throw error;
  } finally {
    isRefreshing = false;
  }
};
