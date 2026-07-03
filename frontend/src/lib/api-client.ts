import axios from "axios";
import type { ApiResponse } from "@/types/api";
import { API_BASE_URL } from "@/lib/constants";

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  const token =
    typeof window !== "undefined" ? localStorage.getItem("accessToken") : null;

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const apiError = error.response.data as ApiResponse<unknown>;
      return Promise.reject(apiError);
    }

    return Promise.reject(error);
  },
);

export { apiClient };
