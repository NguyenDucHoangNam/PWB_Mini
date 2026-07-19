import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ProcessingStatus } from "../types";
import { songKey } from "./songs";

export const SONG_PROCESSING_KEY = "voice-song-processing" as const;
export const songProcessingKey = (songId: string) =>
  ["voice-song-processing", songId] as const;

export const triggerProcessing = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<ProcessingStatus>> =>
  apiClient
    .post(`/songs/${songId}/process`)
    .then((res) => res.data);

export const getProcessingStatus = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<ProcessingStatus>> =>
  apiClient
    .get(`/songs/${songId}/status`)
    .then((res) => res.data);

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
        queryClient.invalidateQueries({
          queryKey: songProcessingKey(variables.songId),
        });
        queryClient.invalidateQueries({
          queryKey: songKey(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: triggerProcessing,
  });
};

type UseProcessingStatusOptions = {
  queryConfig?: QueryConfig<typeof getProcessingStatus>;
};

export const useProcessingStatus = ({
  songId,
  enabled = true,
  queryConfig,
}: {
  songId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getProcessingStatus>;
}) =>
  useQuery({
    queryKey: songProcessingKey(songId),
    queryFn: () => getProcessingStatus({ songId }),
    enabled: enabled && Boolean(songId),
    ...queryConfig,
  });