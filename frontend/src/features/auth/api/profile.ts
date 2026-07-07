import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { UserProfileResponse, UpdateProfileRequest } from "../types";

export const getProfile = (): Promise<ApiResponse<UserProfileResponse>> => {
  return apiClient.get("/auth/me").then((res) => res.data);
};

export const updateProfile = ({
  data,
}: {
  data: UpdateProfileRequest;
}): Promise<ApiResponse<UserProfileResponse>> => {
  return apiClient.put("/auth/profile", data).then((res) => res.data);
};

type UseProfileOptions = {
  queryConfig?: QueryConfig<typeof getProfile>;
};

export const useProfile = ({ queryConfig }: UseProfileOptions = {}) => {
  return useQuery({
    queryKey: ["auth-profile"],
    queryFn: getProfile,
    ...queryConfig,
  });
};

type UseUpdateProfileOptions = {
  mutationConfig?: MutationConfig<typeof updateProfile>;
};

export const useUpdateProfile = ({ mutationConfig }: UseUpdateProfileOptions = {}) => {
  const queryClient = useQueryClient();

  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: ["auth-profile"] });
      }
    },
    ...mutationConfig,
    mutationFn: updateProfile,
  });
};
