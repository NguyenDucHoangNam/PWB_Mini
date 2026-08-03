import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { AudioUrl } from "../types";

export const SONG_STREAM_KEY = "voice-song-stream" as const;
export const songStreamKey = (songId: string, variant: string) =>
  ["voice-song-stream", songId, variant] as const;

export const getOriginalUrl = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/songs/${songId}/audio-url`, { params: { variant: "ORIGINAL" } })
    .then((res) => res.data);

export const getProcessedUrl = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/songs/${songId}/audio-url`, { params: { variant: "PROCESSED" } })
    .then((res) => res.data);

export const useOriginalUrl = ({
  songId,
  enabled = true,
  queryConfig,
}: {
  songId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getOriginalUrl>;
}) =>
  useQuery({
    queryKey: songStreamKey(songId, "original"),
    queryFn: () => getOriginalUrl({ songId }),
    enabled: enabled && Boolean(songId),
    retry: false,
    ...queryConfig,
  });

export const useProcessedUrl = ({
  songId,
  enabled = true,
  queryConfig,
}: {
  songId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getProcessedUrl>;
}) =>
  useQuery({
    queryKey: songStreamKey(songId, "processed"),
    queryFn: () => getProcessedUrl({ songId }),
    enabled: enabled && Boolean(songId),
    retry: false,
    ...queryConfig,
  });