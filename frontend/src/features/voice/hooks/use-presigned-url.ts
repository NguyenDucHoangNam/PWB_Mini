"use client";

import { useEffect } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import type { ApiResponse } from "@/types/api";
import type { AudioUrl } from "../types";

const REFRESH_BUFFER_MS = 5 * 60 * 1000;

type Fetcher = () => Promise<ApiResponse<AudioUrl>>;

export function usePresignedUrl({
  fetcher,
  enabled,
  queryKey,
}: {
  fetcher: Fetcher;
  enabled: boolean;
  queryKey: readonly unknown[];
}): UseQueryResult<ApiResponse<AudioUrl>, Error> {
  const query = useQuery({
    queryKey,
    queryFn: fetcher,
    enabled,
    staleTime: REFRESH_BUFFER_MS,
    retry: false,
  });

  const expiresAt = query.data?.data?.expiresAt
    ? new Date(query.data.data.expiresAt).getTime()
    : 0;
  const remainingMs = expiresAt > 0 ? expiresAt - Date.now() : 0;
  const isNearExpiry =
    expiresAt > 0 && remainingMs > 0 && remainingMs < REFRESH_BUFFER_MS;

  useEffect(() => {
    if (!isNearExpiry || !enabled || !query.data?.data?.url) return;
    const timeoutMs = Math.max(0, remainingMs - REFRESH_BUFFER_MS);
    const timer = setTimeout(() => {
      query.refetch();
    }, timeoutMs);
    return () => clearTimeout(timer);
  }, [isNearExpiry, remainingMs, enabled, query.data?.data?.url, query.refetch]);

  return query;
}