import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { LoginRequest, LoginResponse, Oauth2LoginRequest } from "../types";

export const login = ({
  data,
}: {
  data: LoginRequest;
}): Promise<ApiResponse<LoginResponse>> => {
  return apiClient.post("/auth/login", data).then((res) => res.data);
};

export const loginWithGoogle = ({
  data,
}: {
  data: Oauth2LoginRequest;
}): Promise<ApiResponse<LoginResponse>> => {
  return apiClient.post("/auth/login/google", data).then((res) => res.data);
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
