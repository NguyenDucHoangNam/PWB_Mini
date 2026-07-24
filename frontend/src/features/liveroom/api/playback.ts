import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { PlaybackSnapshot } from "../types";
import type { AudioUrl } from "@/features/voice/types";

export const PLAYBACK_KEY = "liveroom-playback" as const;

export const playbackKey = (roomCode: string) =>
  ["liveroom-playback", roomCode] as const;

export const sharedStreamKey = (
  roomCode: string,
  songId: string | null | undefined,
) => ["liveroom-playback", "stream", roomCode, songId] as const;

export const getPlayback = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<PlaybackSnapshot>> =>
  apiClient
    .get(`/live-rooms/${roomCode}/playback`)
    .then((res) => res.data);

export const getSharedStreamUrl = ({
  roomCode,
  songId,
  ttlSeconds,
}: {
  roomCode: string;
  songId: string;
  ttlSeconds?: number;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/live-rooms/${roomCode}/playback/songs/${songId}/stream`, {
      params: ttlSeconds ? { ttlSeconds } : undefined,
    })
    .then((res) => res.data);

export const selectPlaybackSong = ({
  roomCode,
  songId,
}: {
  roomCode: string;
  songId: string;
}): Promise<ApiResponse<PlaybackSnapshot>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/playback/songs`, { songId })
    .then((res) => res.data);

export const playPlayback = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<PlaybackSnapshot>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/playback/play`, {})
    .then((res) => res.data);

export const pausePlayback = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<PlaybackSnapshot>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/playback/pause`, {})
    .then((res) => res.data);

type UsePlaybackOptions = {
  queryConfig?: QueryConfig<typeof getPlayback>;
};

export const usePlayback = ({
  roomCode,
  enabled = true,
  queryConfig,
}: {
  roomCode: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getPlayback>;
}) =>
  useQuery({
    queryKey: playbackKey(roomCode),
    queryFn: () => getPlayback({ roomCode }),
    enabled: enabled && Boolean(roomCode),
    ...queryConfig,
  });

type UseSharedStreamOptions = {
  queryConfig?: QueryConfig<typeof getSharedStreamUrl>;
};

export const useSharedStreamUrl = ({
  roomCode,
  songId,
  ttlSeconds,
  enabled = true,
  queryConfig,
}: {
  roomCode: string;
  songId: string | null | undefined;
  ttlSeconds?: number;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getSharedStreamUrl>;
}) =>
  useQuery({
    queryKey: sharedStreamKey(roomCode, songId),
    queryFn: () => {
      if (!songId) {
        throw new Error("songId is required");
      }
      return getSharedStreamUrl({ roomCode, songId, ttlSeconds });
    },
    enabled: enabled && Boolean(roomCode) && Boolean(songId),
    retry: false,
    ...queryConfig,
  });

type UseSelectSongOptions = {
  mutationConfig?: MutationConfig<typeof selectPlaybackSong>;
};

export const useSelectPlaybackSong = ({
  mutationConfig,
}: UseSelectSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: playbackKey(variables.roomCode),
        });
      }
    },
    ...mutationConfig,
    mutationFn: selectPlaybackSong,
  });
};

type UseControlOptions = {
  mutationConfig?: MutationConfig<typeof playPlayback>;
};

const PLAYBACK_INVALIDATE_DEBOUNCE_MS = 250;

const usePlaybackMutationInvalidation = (
  queryClient: ReturnType<typeof useQueryClient>,
  roomCode: string,
) => {
  const ref = { scheduled: false };
  return () => {
    if (ref.scheduled) {
      return;
    }
    ref.scheduled = true;
    setTimeout(() => {
      queryClient.invalidateQueries({ queryKey: playbackKey(roomCode) });
      ref.scheduled = false;
    }, PLAYBACK_INVALIDATE_DEBOUNCE_MS);
  };
};

export const usePlayPlayback = ({
  mutationConfig,
}: UseControlOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        const invalidate =
          usePlaybackMutationInvalidation(queryClient, variables.roomCode);
        invalidate();
      }
    },
    ...mutationConfig,
    mutationFn: playPlayback,
  });
};

export const usePausePlayback = ({
  mutationConfig,
}: UseControlOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        const invalidate =
          usePlaybackMutationInvalidation(queryClient, variables.roomCode);
        invalidate();
      }
    },
    ...mutationConfig,
    mutationFn: pausePlayback,
  });
};
