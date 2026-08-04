import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ForgotPasswordRequest } from "../types";

export const forgotPassword = ({
  data,
}: {
  data: ForgotPasswordRequest;
}): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/forgot-password", data).then((res) => res.data);
};

type UseForgotPasswordOptions = {
  mutationConfig?: MutationConfig<typeof forgotPassword>;
};

export const useForgotPassword = ({ mutationConfig }: UseForgotPasswordOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: forgotPassword,
  });
};
