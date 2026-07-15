import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";

export const logout = (): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/logout").then((res) => res.data);
};

type UseLogoutOptions = {
  mutationConfig?: MutationConfig<typeof logout>;
};

export const useLogout = ({ mutationConfig }: UseLogoutOptions = {}) => {
  const clearAuth = useAuthStore((state) => state.clearAuth);

  return useMutation({
    onSettled: () => {
      clearAuth();
      broadcastAuthMessage({ type: "LOGOUT" });
    },
    ...mutationConfig,
    mutationFn: logout,
  });
};
