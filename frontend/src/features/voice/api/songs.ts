import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  ListSongsParams,
  Song,
  SongStatus,
  UpdateSongRequest,
  UploadSongRequest,
} from "../types";

export const SONGS_KEY = "voice-songs" as const;
export const songKey = (songId: string) =>
  ["voice-songs", songId] as const;

export const getPresignedUploadUrl = ({
  format,
}: {
  format: string;
}): Promise<ApiResponse<{ originalS3Key: string; uploadUrl: string; expiresInSeconds: number }>> =>
  apiClient
    .post("/songs/presigned-upload-url", { format })
    .then((res) => res.data);

export const uploadSong = (
  data: UploadSongRequest,
): Promise<ApiResponse<Song>> =>
  apiClient.post("/songs/upload", data).then((res) => res.data);

export const listSongs = ({
  page,
  size,
  status,
}: ListSongsParams): Promise<ApiResponse<PaginatedResponse<Song>>> =>
  apiClient
    .get("/songs", { params: { page, size, status } })
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
  apiClient.put(`/songs/${songId}`, data).then((res) => res.data);

export const deleteSong = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<void>> =>
  apiClient.delete(`/songs/${songId}`).then((res) => res.data);



type UseUploadSongOptions = {
  mutationConfig?: MutationConfig<typeof uploadSong>;
};

export const useUploadSong = ({
  mutationConfig,
}: UseUploadSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: uploadSong,
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
    queryKey: [SONGS_KEY, { page, size, status }],
    queryFn: () => listSongs({ page, size, status }),
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
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
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
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: deleteSong,
  });
};


export type { Song, SongStatus };