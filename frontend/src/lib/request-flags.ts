import type { AxiosRequestConfig } from "axios";

export const SKIP_REFRESH_HEADER = "__skipRefresh" as const;

declare module "axios" {
  export interface AxiosRequestConfig {
    [SKIP_REFRESH_HEADER]?: boolean;
  }
}

export function shouldSkipRefresh(config: AxiosRequestConfig | undefined): boolean {
  return Boolean(config && config[SKIP_REFRESH_HEADER]);
}

export function withSkipRefresh(config: AxiosRequestConfig): AxiosRequestConfig {
  return {
    ...config,
    [SKIP_REFRESH_HEADER]: true,
  };
}
