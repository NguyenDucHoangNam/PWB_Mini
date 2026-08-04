import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { VerifyOtpRequest, VerifyOtpResponse } from "../types";

export const verifyOtp = ({
  data,
}: {
  data: VerifyOtpRequest;
}): Promise<ApiResponse<VerifyOtpResponse>> => {
  return apiClient.post("/auth/verify-otp", data).then((res) => res.data);
};

type UseVerifyOtpOptions = {
  mutationConfig?: MutationConfig<typeof verifyOtp>;
};

export const useVerifyOtp = ({ mutationConfig }: UseVerifyOtpOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: verifyOtp,
  });
};
