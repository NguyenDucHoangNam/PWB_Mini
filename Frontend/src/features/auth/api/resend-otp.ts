import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ResendOtpRequest, ResendOtpResponse } from "../types";

export const resendOtp = ({ data }: { data: ResendOtpRequest }): Promise<ApiResponse<ResendOtpResponse>> => {
  return apiClient.post("/auth/resend-otp", data).then((res) => res.data);
};

type UseResendOtpOptions = {
  mutationConfig?: MutationConfig<typeof resendOtp>;
};

export const useResendOtp = ({ mutationConfig }: UseResendOtpOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: resendOtp,
  });
};
