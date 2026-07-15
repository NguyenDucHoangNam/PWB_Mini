import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type {
  AuthResponse,
  OAuth2LoginRequest,
} from "../types";

export const login = ({
  data,
}: {
  data: { email: string; password: string };
}): Promise<ApiResponse<AuthResponse>> => {
  return apiClient.post<ApiResponse<AuthResponse>>("/auth/login", data).then((res) => res.data);
};

export const loginWithGoogle = ({
  data,
}: {
  data: OAuth2LoginRequest;
}): Promise<ApiResponse<AuthResponse>> => {
  return apiClient.post<ApiResponse<AuthResponse>>("/auth/google", data).then((res) => res.data);
};

type UseLoginOptions = {
  mutationConfig?: MutationConfig<typeof login>;
};

export const useLogin = ({ mutationConfig }: UseLoginOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: login,
  });
};

type UseLoginWithGoogleOptions = {
  mutationConfig?: MutationConfig<typeof loginWithGoogle>;
};

export const useLoginWithGoogle = ({ mutationConfig }: UseLoginWithGoogleOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: loginWithGoogle,
  });
};
