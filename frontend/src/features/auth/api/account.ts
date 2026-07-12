import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { useAuthStore } from "../stores/use-auth-store";
import { broadcastAuthMessage } from "@/lib/broadcast-channel";
import type { AvatarUploadResponse, DeleteAccountRequest, UserProfileResponse } from "../types";

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

export const cancelDeletion = (): Promise<ApiResponse<UserProfileResponse>> => {
  return apiClient
    .post<ApiResponse<UserProfileResponse>>("/auth/account/cancel-deletion")
    .then((res) => res.data);
};

export const uploadAvatar = (file: File): Promise<ApiResponse<AvatarUploadResponse>> => {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient
    .post<ApiResponse<AvatarUploadResponse>>("/auth/profile/avatar", formData, {
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
    onSettled: () => {
      clearAuth();
      broadcastAuthMessage({ type: "LOGOUT" });
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
      if (response.success && response.data) {
        const current = useAuthStore.getState().user;
        if (current) {
          useAuthStore.getState().setUser({
            ...current,
            fullName: response.data.fullName,
            email: response.data.email,
            role: response.data.role,
            status: response.data.status,
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
    mutationFn: async (file: File): Promise<ApiResponse<AvatarUploadResponse>> => {
      return uploadAvatar(file);
    },
  });
};
