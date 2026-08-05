import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { AudioUrl } from "../types";

export const SONG_STREAM_KEY = "voice-song-stream" as const;
export const songStreamKey = (songId: string) =>
  ["voice-song-stream", songId] as const;

/**
 * A song has exactly one playable rendition: merged with its voice tag if it was uploaded with one, the
 * plain upload otherwise. The server decides — there is nothing for the caller to pick.
 */
export const getSongAudioUrl = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient.get(`/songs/${songId}/audio-url`).then((res) => res.data);

export const useSongAudioUrl = ({
  songId,
  enabled = true,
  queryConfig,
}: {
  songId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getSongAudioUrl>;
}) =>
  useQuery({
    queryKey: songStreamKey(songId),
    queryFn: () => getSongAudioUrl({ songId }),
    enabled: enabled && Boolean(songId),
    retry: false,
    ...queryConfig,
  });
