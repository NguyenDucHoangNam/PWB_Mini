import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import type { DeleteAccountRequest } from "../types";

export const deleteAccount = ({
  data,
}: {
  data: DeleteAccountRequest;
}): Promise<ApiResponse<void>> => {
  return apiClient.delete("/auth/account", { data }).then((res) => res.data);
};

export const logout = (): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/logout").then((res) => res.data);
};

type UseDeleteAccountOptions = {
  mutationConfig?: MutationConfig<typeof deleteAccount>;
};

export const useDeleteAccount = ({ mutationConfig }: UseDeleteAccountOptions = {}) => {
  const clearAuth = useAuthStore((state) => state.clearAuth);

  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        clearAuth();
      }
    },
    ...mutationConfig,
    mutationFn: deleteAccount,
  });
};

type UseLogoutOptions = {
  mutationConfig?: MutationConfig<typeof logout>;
};

export const useLogout = ({ mutationConfig }: UseLogoutOptions = {}) => {
  const clearAuth = useAuthStore((state) => state.clearAuth);

  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        clearAuth();
      }
    },
    ...mutationConfig,
    mutationFn: logout,
  });
};
