import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
} from "@tanstack/react-query";
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
  status,
}: ListSongsParams): Promise<ApiResponse<PaginatedResponse<Song>>> =>
  apiClient
    .get("/songs", { params: { page, size, status: status || undefined } })
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

/** Answers 204 No Content — there is no envelope to unwrap, so callers get nothing back. */
export const deleteSong = ({
  songId,
}: {
  songId: string;
}): Promise<void> =>
  apiClient.delete(`/songs/${songId}`).then(() => undefined);

export const getVoiceTagConfig = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<VoiceTagConfig | null>> =>
  apiClient.get(`/songs/${songId}/voice-tag-config`).then((res) => res.data);

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

export const songVoiceTagConfigKey = (songId: string) =>
  ["voice-songs", songId, "voice-tag-config"] as const;

type UseVoiceTagConfigOptions = {
  queryConfig?: QueryConfig<typeof getVoiceTagConfig>;
};

export const useVoiceTagConfig = ({
  songId,
  queryConfig,
}: { songId: string } & UseVoiceTagConfigOptions) =>
  useQuery({
    queryKey: songVoiceTagConfigKey(songId),
    queryFn: () => getVoiceTagConfig({ songId }),
    enabled: Boolean(songId),
    ...queryConfig,
  });

type UseCreateSongOptions = {
  mutationConfig?: MutationConfig<typeof createSong>;
};

export const useCreateSong = ({
  mutationConfig,
}: UseCreateSongOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: createSong,
  });
};

type UseListSongsOptions = {
  queryConfig?: QueryConfig<typeof listSongs>;
};

export const useListSongs = ({
  page,
  size,
  status,
  queryConfig,
}: ListSongsParams & UseListSongsOptions) =>
  useQuery({
    queryKey: [SONGS_KEY, { page, size, status: status ?? null }],
    queryFn: () => listSongs({ page, size, status }),
    // Holding the previous page while the next one loads keeps the list from collapsing to a spinner
    // on every paging click. Mutations invalidate this key, so freshness does not depend on staleTime.
    placeholderData: keepPreviousData,
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
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
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
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      queryClient.removeQueries({ queryKey: songKey(variables.songId) });
      return onSuccess?.(response, variables, onMutateResult, context);
    },
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
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: songKey(variables.songId) });
        queryClient.invalidateQueries({
          queryKey: songVoiceTagConfigKey(variables.songId),
        });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
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
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: songKey(variables.songId) });
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: triggerProcessing,
  });
};

export type { Song, SongStatus };