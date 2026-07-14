import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import type {
  LoginRequest,
  LoginResponse,
  Oauth2LoginRequest,
  RegisterRequest,
  RegisterResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest,
} from "../types";

export function useLogin() {
  return useMutation({
    mutationFn: async ({ data }: { data: LoginRequest }): Promise<ApiResponse<LoginResponse>> => {
      const res = await apiClient.post<ApiResponse<LoginResponse>>("/auth/login", data);
      return res.data;
    },
  });
}

export function useLoginWithGoogle() {
  return useMutation({
    mutationFn: async ({
      data,
    }: {
      data: Oauth2LoginRequest;
    }): Promise<ApiResponse<LoginResponse>> => {
      const res = await apiClient.post<ApiResponse<LoginResponse>>("/auth/login/google", data);
      return res.data;
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: async ({
      data,
    }: {
      data: RegisterRequest;
    }): Promise<ApiResponse<RegisterResponse>> => {
      const res = await apiClient.post<ApiResponse<RegisterResponse>>("/auth/register", data);
      return res.data;
    },
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: async ({ data }: { data: ForgotPasswordRequest }) => {
      const res = await apiClient.post<ApiResponse<unknown>>("/auth/forgot-password", data);
      return res.data;
    },
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: async ({ data }: { data: ResetPasswordRequest }) => {
      const res = await apiClient.post<ApiResponse<unknown>>("/auth/reset-password", data);
      return res.data;
    },
  });
}
