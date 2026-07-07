import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ResetPasswordRequest } from "../types";

export const resetPassword = ({
  data,
}: {
  data: ResetPasswordRequest;
}): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/reset-password", data).then((res) => res.data);
};

type UseResetPasswordOptions = {
  mutationConfig?: MutationConfig<typeof resetPassword>;
};

export const useResetPassword = ({ mutationConfig }: UseResetPasswordOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: resetPassword,
  });
};
