import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  AudioUrl,
  ConfigureVoiceTagRequest,
  CreateSongRequest,
  ListSongsParams,
  Song,
  SongStatus,
  VoiceTagConfig,
  UpdateSongRequest,
  UploadUrlResponse,
} from "../types";

export const SONGS_KEY = "voice-songs" as const;
export const songKey = (songId: string) =>
  ["voice-songs", songId] as const;

export const getPresignedUploadUrl = ({
  format,
}: {
  format: string;
}): Promise<ApiResponse<UploadUrlResponse>> =>
  apiClient
    .post("/songs/upload-url", { format })
    .then((res) => res.data);

export const createSong = (
  data: CreateSongRequest,
): Promise<ApiResponse<Song>> =>
  apiClient.post("/songs", data).then((res) => res.data);

export const listSongs = ({
  page,
  size,
}: ListSongsParams): Promise<ApiResponse<PaginatedResponse<Song>>> =>
  apiClient
    .get("/songs", { params: { page, size } })
    .then((res) => res.data);

export const getSong = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<Song>> =>
  apiClient.get(`/songs/${songId}`).then((res) => res.data);

export const updateSong = ({
  songId,
  data,
}: {
  songId: string;
  data: UpdateSongRequest;
}): Promise<ApiResponse<Song>> =>
  apiClient.patch(`/songs/${songId}`, data).then((res) => res.data);

export const deleteSong = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<void>> =>
  apiClient.delete(`/songs/${songId}`).then((res) => res.data);

export const configureVoiceTag = ({
  songId,
  data,
}: {
  songId: string;
  data: ConfigureVoiceTagRequest;
}): Promise<ApiResponse<VoiceTagConfig>> =>
  apiClient.put(`/songs/${songId}/voice-tag-config`, data).then((res) => res.data);

export const triggerProcessing = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<Song>> =>
  apiClient.post(`/songs/${songId}/trigger-processing`).then((res) => res.data);

type UseCreateSongOptions = {
  mutationConfig?: MutationConfig<typeof createSong>;
};

export const useCreateSong = ({
  mutationConfig,
}: UseCreateSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
      return mutationConfig?.onSuccess?.(response, variables, onMutateResult, context);
    },
    ...mutationConfig,
    mutationFn: createSong,
  });
};

type UseListSongsOptions = {
  queryConfig?: QueryConfig<typeof listSongs>;
};

export const useListSongs = ({
  page,
  size,
  queryConfig,
}: ListSongsParams & UseListSongsOptions) =>
  useQuery({
    queryKey: [SONGS_KEY, { page, size }],
    queryFn: () => listSongs({ page, size }),
    staleTime: 0,
    ...queryConfig,
  });

type UseSongOptions = {
  queryConfig?: QueryConfig<typeof getSong>;
};

export const useSong = ({
  songId,
  queryConfig,
}: {
  songId: string;
  queryConfig?: QueryConfig<typeof getSong>;
}) =>
  useQuery({
    queryKey: songKey(songId),
    queryFn: () => getSong({ songId }),
    enabled: Boolean(songId),
    ...queryConfig,
  });

type UseUpdateSongOptions = {
  mutationConfig?: MutationConfig<typeof updateSong>;
};

export const useUpdateSong = ({
  mutationConfig,
}: UseUpdateSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
      return mutationConfig?.onSuccess?.(response, variables, onMutateResult, context);
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
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
      return mutationConfig?.onSuccess?.(response, variables, onMutateResult, context);
    },
    ...mutationConfig,
    mutationFn: deleteSong,
  });
};

type UseConfigureVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof configureVoiceTag>;
};

export const useConfigureVoiceTag = ({
  mutationConfig,
}: UseConfigureVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: songKey(variables.songId) });
      }
      return mutationConfig?.onSuccess?.(response, variables, onMutateResult, context);
    },
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
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: songKey(variables.songId) });
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
      return mutationConfig?.onSuccess?.(response, variables, onMutateResult, context);
    },
    ...mutationConfig,
    mutationFn: triggerProcessing,
  });
};

export type { Song, SongStatus };