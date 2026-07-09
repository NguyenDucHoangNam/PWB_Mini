import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
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

export const cancelDeletion = (): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/account/cancel-deletion").then((res) => res.data);
};

export const uploadAvatar = (file: File): Promise<ApiResponse<{ avatarUrl: string }>> => {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient
    .post<ApiResponse<{ avatarUrl: string }>>("/auth/profile/avatar", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    })
    .then((res) => res.data);
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
        broadcastAuthMessage({ type: "LOGOUT" });
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
        broadcastAuthMessage({ type: "LOGOUT" });
      }
    },
    ...mutationConfig,
    mutationFn: logout,
  });
};

type UseCancelDeletionOptions = {
  mutationConfig?: MutationConfig<typeof cancelDeletion>;
};

export const useCancelDeletion = ({ mutationConfig }: UseCancelDeletionOptions = {}) => {
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        const current = useAuthStore.getState().user;
        if (current) {
          // AuthUser doesn't carry deletionRequestedAt directly; fall back to
          // updating only the status so the dashboard recognises ACTIVE.
          useAuthStore.setState({
            user: { ...current, status: "ACTIVE" },
          });
        }
      }
    },
    ...mutationConfig,
    mutationFn: cancelDeletion,
  });
};

export const useUploadAvatar = () => {
  return useMutation({
    mutationFn: async (file: File): Promise<ApiResponse<{ avatarUrl: string }>> => {
      return uploadAvatar(file);
    },
    onSuccess: (response) => {
      // Consumer can read response.data.avatarUrl and call setUser if needed.
      return response;
    },
  });
};