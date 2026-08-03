import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  ConfigureVoiceTagRequest,
  CreateSongRequest,
  SongListItem,
  SongResponse,
  UpdateSongRequest,
  UploadUrlRequest,
  UploadUrlResponse,
  AudioUrlResponse,
} from "../types";

export const getSongs = ({
  page,
  size,
}: {
  page: number;
  size: number;
}): Promise<ApiResponse<PaginatedResponse<SongListItem>>> => {
  return apiClient
    .get("/songs", { params: { page, size } })
    .then((res) => res.data);
};

export const getSong = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<SongResponse>> => {
  return apiClient.get(`/songs/${songId}`).then((res) => res.data);
};

export const getAudioUrl = ({
  songId,
  variant = "PROCESSED",
  expiresIn = 3600,
}: {
  songId: string;
  variant?: "ORIGINAL" | "PROCESSED";
  expiresIn?: number;
}): Promise<ApiResponse<AudioUrlResponse>> => {
  return apiClient
    .get(`/songs/${songId}/audio-url`, { params: { variant, expiresIn } })
    .then((res) => res.data);
};

export const requestUploadUrl = ({
  data,
}: {
  data: UploadUrlRequest;
}): Promise<ApiResponse<UploadUrlResponse>> => {
  return apiClient.post("/songs/upload-url", data).then((res) => res.data);
};

export const createSong = ({
  data,
}: {
  data: CreateSongRequest;
}): Promise<ApiResponse<SongResponse>> => {
  return apiClient.post("/songs", data).then((res) => res.data);
};

export const configureVoiceTag = ({
  songId,
  data,
}: {
  songId: string;
  data: ConfigureVoiceTagRequest;
}): Promise<ApiResponse<unknown>> => {
  return apiClient.put(`/songs/${songId}/voice-tag-config`, data).then((res) => res.data);
};

export const triggerProcessing = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<SongResponse>> => {
  return apiClient.post(`/songs/${songId}/trigger-processing`).then((res) => res.data);
};

export const updateSong = ({
  songId,
  data,
}: {
  songId: string;
  data: UpdateSongRequest;
}): Promise<ApiResponse<SongResponse>> => {
  return apiClient.patch(`/songs/${songId}`, data).then((res) => res.data);
};

export const deleteSong = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<void>> => {
  return apiClient.delete(`/songs/${songId}`).then((res) => res.data);
};

export const SONGS_KEY = "audio-songs" as const;
export const SONG_KEY = (songId: string) => ["audio-song", songId] as const;
export const AUDIO_URL_KEY = (songId: string, variant: string) =>
  ["audio-url", songId, variant] as const;

type UseCreateSongOptions = {
  mutationConfig?: MutationConfig<typeof createSong>;
};

export const useCreateSong = ({
  mutationConfig,
}: UseCreateSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: createSong,
  });
};

type UseConfigureVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof configureVoiceTag>;
};

export const useConfigureVoiceTag = ({
  mutationConfig,
}: UseConfigureVoiceTagOptions = {}) => {
  return useMutation({
    ...mutationConfig,
    mutationFn: configureVoiceTag,
  });
};

type UseTriggerProcessingOptions = {
  mutationConfig?: MutationConfig<typeof triggerProcessing>;
};

export const useTriggerProcessing = ({
  mutationConfig,
}: UseTriggerProcessingOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: SONG_KEY(variables.songId) });
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: triggerProcessing,
  });
};

type UseUpdateSongOptions = {
  mutationConfig?: MutationConfig<typeof updateSong>;
};

export const useUpdateSong = ({
  mutationConfig,
}: UseUpdateSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: SONG_KEY(variables.songId) });
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: updateSong,
  });
};

type UseDeleteSongOptions = {
  mutationConfig?: MutationConfig<typeof deleteSong>;
};

export const useDeleteSong = ({
  mutationConfig,
}: UseDeleteSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: deleteSong,
  });
};

export function useSongs({
  page,
  size,
  queryConfig,
}: {
  page: number;
  size: number;
  queryConfig?: QueryConfig<typeof getSongs>;
}) {
  return useQuery({
    queryKey: [SONGS_KEY, { page, size }],
    queryFn: () => getSongs({ page, size }),
    ...queryConfig,
  });
}

export function useSong({
  songId,
  queryConfig,
}: {
  songId: string;
  queryConfig?: QueryConfig<typeof getSong>;
}) {
  return useQuery({
    queryKey: SONG_KEY(songId),
    queryFn: () => getSong({ songId }),
    enabled: Boolean(songId),
    ...queryConfig,
  });
}

export function useAudioUrl({
  songId,
  variant = "PROCESSED",
  queryConfig,
}: {
  songId: string;
  variant?: "ORIGINAL" | "PROCESSED";
  queryConfig?: QueryConfig<typeof getAudioUrl>;
}) {
  return useQuery({
    queryKey: AUDIO_URL_KEY(songId, variant),
    queryFn: () => getAudioUrl({ songId, variant }),
    enabled: Boolean(songId),
    ...queryConfig,
  });
}
