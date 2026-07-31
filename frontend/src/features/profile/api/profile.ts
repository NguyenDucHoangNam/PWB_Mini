import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type {
  ProfileResponse,
  UpdateProfileRequest,
  AvatarUploadResponse,
} from "../types";

export const PROFILE_KEY = "profile" as const;

export const getProfile = (): Promise<ApiResponse<ProfileResponse>> => {
  return apiClient.get("/profile").then((res) => res.data);
};

export const updateProfile = ({
  data,
}: {
  data: UpdateProfileRequest;
}): Promise<ApiResponse<ProfileResponse>> => {
  return apiClient.put("/profile", data).then((res) => res.data);
};

export const uploadAvatar = (file: File): Promise<ApiResponse<AvatarUploadResponse>> => {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient
    .post("/profile/avatar", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    })
    .then((res) => res.data);
};

type UseProfileOptions = {
  queryConfig?: {
    staleTime?: number;
    enabled?: boolean;
  };
};

export const useProfile = ({ queryConfig }: UseProfileOptions = {}) => {
  return useQuery({
    queryKey: [PROFILE_KEY],
    queryFn: getProfile,
    staleTime: queryConfig?.staleTime ?? 5 * 60 * 1000,
    enabled: queryConfig?.enabled,
  });
};

type UseUpdateProfileOptions = {
  mutationConfig?: MutationConfig<typeof updateProfile>;
};

export const useUpdateProfile = ({ mutationConfig }: UseUpdateProfileOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    ...mutationConfig,
    mutationFn: updateProfile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PROFILE_KEY] });
    },
  });
};

type UseUploadAvatarOptions = {
  mutationConfig?: MutationConfig<typeof uploadAvatar>;
};

export const useUploadAvatar = ({ mutationConfig }: UseUploadAvatarOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    ...mutationConfig,
    mutationFn: uploadAvatar,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PROFILE_KEY] });
    },
  });
};
