import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  ConfigureVoiceTagRequest,
  ListSongsParams,
  Song,
  SongStatus,
  UpdateSongRequest,
  UploadSongRequest,
  VoiceTagConfig,
} from "../types";

export const SONGS_KEY = "voice-songs" as const;
export const songKey = (songId: string) =>
  ["voice-songs", songId] as const;
export const songConfigKey = (songId: string) =>
  ["voice-songs", songId, "voice-tag-config"] as const;

export const uploadSong = ({
  formData,
}: {
  formData: FormData;
}): Promise<ApiResponse<Song>> =>
  apiClient
    .post("/songs/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      timeout: 120000,
    })
    .then((res) => res.data);

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

export const configureVoiceTag = ({
  songId,
  data,
}: {
  songId: string;
  data: ConfigureVoiceTagRequest;
}): Promise<ApiResponse<VoiceTagConfig>> =>
  apiClient
    .post(`/songs/${songId}/voice-tag`, data)
    .then((res) => res.data);

export const getVoiceTagConfig = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<VoiceTagConfig>> =>
  apiClient
    .get(`/songs/${songId}/voice-tag`)
    .then((res) => res.data);

export const removeVoiceTagConfig = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<void>> =>
  apiClient
    .delete(`/songs/${songId}/voice-tag`)
    .then((res) => res.data);

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
        queryClient.invalidateQueries({
          queryKey: songConfigKey(variables.songId),
        });
      }
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
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: songConfigKey(variables.songId),
        });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: configureVoiceTag,
  });
};

type UseVoiceTagConfigOptions = {
  queryConfig?: QueryConfig<typeof getVoiceTagConfig>;
};

export const useVoiceTagConfig = ({
  songId,
  queryConfig,
}: {
  songId: string;
  queryConfig?: QueryConfig<typeof getVoiceTagConfig>;
}) =>
  useQuery({
    queryKey: songConfigKey(songId),
    queryFn: () => getVoiceTagConfig({ songId }),
    enabled: Boolean(songId),
    retry: false,
    ...queryConfig,
  });

type UseRemoveVoiceTagConfigOptions = {
  mutationConfig?: MutationConfig<typeof removeVoiceTagConfig>;
};

export const useRemoveVoiceTagConfig = ({
  mutationConfig,
}: UseRemoveVoiceTagConfigOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: songConfigKey(variables.songId),
        });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: removeVoiceTagConfig,
  });
};

export type { Song, SongStatus, VoiceTagConfig };