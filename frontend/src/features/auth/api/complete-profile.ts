import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { AuthResponse, CompleteProfileRequest } from "../types";

export const completeProfile = ({
  data,
}: {
  data: CompleteProfileRequest;
}): Promise<ApiResponse<AuthResponse>> => {
  return apiClient.post("/auth/complete-profile", data).then((res) => res.data);
};

type UseCompleteProfileOptions = {
  mutationConfig?: MutationConfig<typeof completeProfile>;
};

export const useCompleteProfile = ({ mutationConfig }: UseCompleteProfileOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: completeProfile,
  });
};
