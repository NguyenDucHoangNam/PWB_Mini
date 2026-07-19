"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getProcessingStatus } from "../api/song-processing";
import { useProcessingStatus } from "../api/song-processing";
import { songKey } from "../api/songs";

const POLL_INTERVAL_MS = 3000;
const MAX_POLLS = 20;

export function useProcessingPolling({
  songId,
  enabled,
}: {
  songId: string;
  enabled: boolean;
}) {
  const [pollCount, setPollCount] = useState(0);
  const queryClient = useQueryClient();

  const query = useProcessingStatus({
    songId,
    enabled,
    queryConfig: {
      refetchInterval: (query) => {
        if (!enabled) return false;
        const status = query?.state?.data?.data?.status;
        if (status === "PROCESSED" || status === "FAILED") return false;
        return POLL_INTERVAL_MS;
      },
      refetchIntervalInBackground: false,
    },
  });

  useEffect(() => {
    if (!query.data) return;
    const status = query.data.data?.status;
    if (status === "PROCESSED" || status === "FAILED") {
      queryClient.invalidateQueries({ queryKey: songKey(songId) });
    }
  }, [query.data, songId, queryClient]);

  useEffect(() => {
    if (query.isFetching) {
      setPollCount((c) => c + 1);
    }
  }, [query.isFetching]);

  const isTimedOut =
    pollCount >= MAX_POLLS && query.data?.data?.status === "PROCESSING";

  return {
    ...query,
    isTimedOut,
    pollCount,
  };
}

export const VOICE_POLLING = {
  intervalMs: POLL_INTERVAL_MS,
  maxPolls: MAX_POLLS,
} as const;

export type ProcessingPollingReturn = ReturnType<typeof useProcessingPolling>;