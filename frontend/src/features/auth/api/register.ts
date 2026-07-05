import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { RegisterRequest, RegisterResponse } from "../types";

export const register = ({
  data,
}: {
  data: RegisterRequest;
}): Promise<ApiResponse<RegisterResponse>> => {
  return apiClient.post("/auth/register", data).then((res) => res.data);
};

type UseRegisterOptions = {
  mutationConfig?: MutationConfig<typeof register>;
};

export const useRegister = ({ mutationConfig }: UseRegisterOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: register,
  });
};
