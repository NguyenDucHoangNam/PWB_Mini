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
  CreateSongRequest,
  ListSongsParams,
  SearchSongsParams,
  Song,
  SongStatus,
  SongSuggestion,
  VoiceTagConfig,
  UpdateSongRequest,
  UploadUrlResponse,
} from "../types";

export const SONGS_KEY = "voice-songs" as const;
export const songKey = (songId: string) =>
  ["voice-songs", songId] as const;

/**
 * `sizeBytes` is required: the server signs it into the URL, so storage refuses a body of any other
 * size. That is the only point at which an upload can be bounded — once the bytes have arrived they are
 * already transferred and billed, and a limit checked afterwards only decides what gets registered.
 */
export const getPresignedUploadUrl = ({
  format,
  sizeBytes,
}: {
  format: string;
  sizeBytes: number;
}): Promise<ApiResponse<UploadUrlResponse>> =>
  apiClient
    .post("/songs/upload-url", { format, sizeBytes })
    .then((res) => res.data);

export const createSong = (
  data: CreateSongRequest,
): Promise<ApiResponse<Song>> =>
  apiClient.post("/songs", data).then((res) => res.data);

/**
 * `status` goes out as a repeated param (`?status=UPLOADED&status=PROCESSED`) because one user-facing
 * filter can cover several job states. Axios's default serializer would otherwise send `status[]=`.
 */
export const listSongs = ({
  page,
  size,
  status,
}: ListSongsParams): Promise<ApiResponse<PaginatedResponse<Song>>> =>
  apiClient
    .get("/songs", {
      params: { page, size, status: status?.length ? status : undefined },
      paramsSerializer: { indexes: null },
    })
    .then((res) => res.data);

/** Same repeated-param treatment for `status` as {@link listSongs}. */
export const searchSongs = ({
  page,
  size,
  q,
  status,
  format,
  minDuration,
  maxDuration,
}: SearchSongsParams): Promise<ApiResponse<PaginatedResponse<Song>>> =>
  apiClient
    .get("/songs/search", {
      params: {
        page,
        size,
        q,
        status: status?.length ? status : undefined,
        format: format ?? undefined,
        minDuration: minDuration ?? undefined,
        maxDuration: maxDuration ?? undefined,
      },
      paramsSerializer: { indexes: null },
    })
    .then((res) => res.data);

export const suggestSongs = ({
  q,
  status,
  limit = 8,
}: {
  q: string;
  status?: SongStatus[] | null;
  limit?: number;
}): Promise<ApiResponse<SongSuggestion[]>> =>
  apiClient
    .get("/songs/suggest", {
      params: { q, limit, status: status?.length ? status : undefined },
      paramsSerializer: { indexes: null },
    })
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

/** Only accepted for a song whose merge failed; the server rejects every other status. */
export const retryProcessing = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<Song>> =>
  apiClient.post(`/songs/${songId}/retry-processing`).then((res) => res.data);

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

type UseSearchSongsOptions = {
  queryConfig?: QueryConfig<typeof searchSongs>;
};

export const useSearchSongs = ({
  queryConfig,
  ...params
}: SearchSongsParams & UseSearchSongsOptions) =>
  useQuery({
    queryKey: [SONGS_KEY, "search", params],
    queryFn: () => searchSongs(params),
    placeholderData: keepPreviousData,
    ...queryConfig,
  });

type UseSuggestSongsOptions = {
  queryConfig?: QueryConfig<typeof suggestSongs>;
};

/**
 * Disabled below two characters: a one-letter prefix matches most of a library, so the request costs a
 * round-trip to produce a list nobody can choose from.
 */
export const useSuggestSongs = ({
  q,
  status,
  limit,
  queryConfig,
}: {
  q: string;
  status?: SongStatus[] | null;
  limit?: number;
} & UseSuggestSongsOptions) =>
  useQuery({
    queryKey: [SONGS_KEY, "suggest", { q, status: status ?? null, limit: limit ?? 8 }],
    queryFn: () => suggestSongs({ q, status, limit }),
    enabled: q.trim().length >= 2,
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

type UseRetryProcessingOptions = {
  mutationConfig?: MutationConfig<typeof retryProcessing>;
};

export const useRetryProcessing = ({
  mutationConfig,
}: UseRetryProcessingOptions = {}) => {
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
    mutationFn: retryProcessing,
  });
};

export type { Song, SongStatus };