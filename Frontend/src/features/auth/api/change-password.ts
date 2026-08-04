import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ChangePasswordRequest } from "../types";

export const changePassword = ({
  data,
}: {
  data: ChangePasswordRequest;
}): Promise<ApiResponse<void>> => {
  return apiClient.post("/auth/change-password", data).then((res) => res.data);
};

type UseChangePasswordOptions = {
  mutationConfig?: MutationConfig<typeof changePassword>;
};

export const useChangePassword = ({ mutationConfig }: UseChangePasswordOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: changePassword,
  });
};
