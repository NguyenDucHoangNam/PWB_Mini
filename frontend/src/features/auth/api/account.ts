import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
import { decodeJwtPayload } from "@/lib/jwt-decode";

interface LogoutPayload {
  refreshToken: string;
  accessToken?: string;
  accessJti?: string;
  accessExpiresInSeconds?: number;
}

function getRefreshTokenFromCookie(): string {
  if (typeof document === "undefined") return "";
  const match = document.cookie.match(/pwb_refresh_token=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : "";
}

export const logout = (): Promise<ApiResponse<void>> => {
  const accessToken = useAuthStore.getState().accessToken;
  const payload: LogoutPayload = { refreshToken: getRefreshTokenFromCookie() };
  if (accessToken) {
    const jwt = decodeJwtPayload(accessToken);
    if (jwt) {
      payload.accessToken = accessToken;
      payload.accessJti = jwt.jti;
      const nowSeconds = Math.floor(Date.now() / 1000);
      payload.accessExpiresInSeconds = Math.max(0, jwt.exp - nowSeconds);
    }
  }
  return apiClient.post("/auth/logout", payload).then((res) => res.data);
};

type UseLogoutOptions = {
  mutationConfig?: MutationConfig<typeof logout>;
};

export const useLogout = ({ mutationConfig }: UseLogoutOptions = {}) => {
  const clearAuth = useAuthStore((state) => state.clearAuth);

  return useMutation({
    onSuccess: () => {
      clearAuth();
      broadcastAuthMessage({ type: "LOGOUT" });
    },
    ...mutationConfig,
    mutationFn: logout,
  });
};
