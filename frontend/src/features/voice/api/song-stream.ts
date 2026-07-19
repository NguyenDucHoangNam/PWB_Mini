import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { AudioUrl } from "../types";

export const SONG_STREAM_KEY = "voice-song-stream" as const;
export const songStreamKey = (songId: string, variant: StreamVariant) =>
  ["voice-song-stream", songId, variant] as const;

export type StreamVariant = "original" | "processed";

export const getStreamUrl = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/songs/${songId}/stream`)
    .then((res) => res.data);

export const getOriginalUrl = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/songs/${songId}/original`)
    .then((res) => res.data);

type UseStreamUrlOptions = {
  queryConfig?: QueryConfig<typeof getStreamUrl>;
};

export const useStreamUrl = ({
  songId,
  enabled = true,
  queryConfig,
}: {
  songId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getStreamUrl>;
}) =>
  useQuery({
    queryKey: songStreamKey(songId, "processed"),
    queryFn: () => getStreamUrl({ songId }),
    enabled: enabled && Boolean(songId),
    retry: false,
    ...queryConfig,
  });

type UseOriginalUrlOptions = {
  queryConfig?: QueryConfig<typeof getOriginalUrl>;
};

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