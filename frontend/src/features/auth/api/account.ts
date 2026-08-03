import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
import { decodeJwtPayload } from "@/lib/jwt-decode";

/**
 * Must stay a subset of the backend `LogoutRequest`. The API's ObjectMapper runs with
 * `FAIL_ON_UNKNOWN_PROPERTIES` enabled, so any extra key here is a 400 rather than a
 * silently ignored field.
 *
 * The refresh token is not sent: it lives in an HttpOnly cookie that JS cannot read, and the
 * browser attaches it automatically because the client is credentialed. Only the access token's
 * identity is passed, so the server can blacklist it for its remaining lifetime.
 */
interface LogoutPayload {
  accessJti?: string;
  accessExpiresInSeconds?: number;
}

export const logout = (): Promise<ApiResponse<void>> => {
  const accessToken = useAuthStore.getState().accessToken;
  const payload: LogoutPayload = {};

  if (accessToken) {
    const jwt = decodeJwtPayload(accessToken);
    if (jwt) {
      payload.accessJti = jwt.jti;
      payload.accessExpiresInSeconds = Math.max(0, jwt.exp - Math.floor(Date.now() / 1000));
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
