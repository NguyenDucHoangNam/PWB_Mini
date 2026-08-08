"use client";

import { useEffect } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import type { ApiResponse } from "@/types/api";
import type { AudioUrl } from "../types";

const REFRESH_BUFFER_MS = 5 * 60 * 1000;

type Fetcher = () => Promise<ApiResponse<AudioUrl>>;

/**
 * Wraps a presigned-URL query and renews it shortly before the URL dies, so a listener who leaves the
 * page open does not hit a 403 mid-playback.
 *
 * The renewal deadline is derived inside the effect rather than during render: reading the clock while
 * rendering makes the delay change on every re-render, which used to re-arm the timer constantly while
 * still never scheduling anything for a URL that was not already expiring.
 */
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

  const expiresAt = query.data?.data?.expiresAt ?? null;
  const { refetch } = query;

  useEffect(() => {
    if (!enabled || !expiresAt) return;

    const expiresAtMs = new Date(expiresAt).getTime();
    if (!Number.isFinite(expiresAtMs)) return;

    // Already inside the buffer means renew now. The refetch yields a new expiry, which re-runs this
    // effect with a fresh deadline; an unchanged expiry leaves the dependency untouched, so a server
    // that keeps handing back the same URL cannot spin this into a loop.
    const delay = Math.max(0, expiresAtMs - REFRESH_BUFFER_MS - Date.now());
    const timer = setTimeout(() => {
      refetch();
    }, delay);

    return () => clearTimeout(timer);
  }, [expiresAt, enabled, refetch]);

  return query;
}
