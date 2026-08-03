import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  DistributionListItem,
  DistributeSongRequest,
  DistributeSongResponse,
} from "../types";
import { SONGS_KEY } from "./audio";

export const DISTRIBUTIONS_KEY = (songId: string) =>
  ["audio-distributions", songId] as const;

export const getDistributions = ({
  songId,
  page,
  size,
  includeRevoked,
}: {
  songId: string;
  page: number;
  size: number;
  includeRevoked: boolean;
}): Promise<ApiResponse<PaginatedResponse<DistributionListItem>>> => {
  return apiClient
    .get(`/songs/${songId}/distributions`, {
      params: { page, size, includeRevoked },
    })
    .then((res) => res.data);
};

export const distributeSong = ({
  songId,
  data,
}: {
  songId: string;
  data: DistributeSongRequest;
}): Promise<ApiResponse<DistributeSongResponse>> => {
  return apiClient
    .post(`/songs/${songId}/distribute`, data)
    .then((res) => res.data);
};

export const revokeDistribution = ({
  songId,
  distributionId,
}: {
  songId: string;
  distributionId: string;
}): Promise<ApiResponse<DistributionListItem>> => {
  return apiClient
    .delete(`/songs/${songId}/distributions/${distributionId}`)
    .then((res) => res.data);
};

export const revokeAllDistributions = ({
  songId,
}: {
  songId: string;
}): Promise<ApiResponse<number>> => {
  return apiClient
    .delete(`/songs/${songId}/distributions`)
    .then((res) => res.data);
};

export const suggestRecipients = ({
  q,
}: {
  q: string;
}): Promise<ApiResponse<string[]>> => {
  return apiClient
    .get("/songs/recipients/suggest", { params: { q } })
    .then((res) => res.data);
};

export function useDistributions({
  songId,
  page,
  size,
  includeRevoked,
  queryConfig,
}: {
  songId: string;
  page: number;
  size: number;
  includeRevoked: boolean;
  queryConfig?: QueryConfig<typeof getDistributions>;
}) {
  return useQuery({
    queryKey: DISTRIBUTIONS_KEY(songId),
    queryFn: () => getDistributions({ songId, page, size, includeRevoked }),
    enabled: Boolean(songId),
    ...queryConfig,
  });
}

type UseDistributeSongOptions = {
  mutationConfig?: MutationConfig<typeof distributeSong>;
};

export const useDistributeSong = ({
  mutationConfig,
}: UseDistributeSongOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: DISTRIBUTIONS_KEY(variables.songId),
        });
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: distributeSong,
  });
};

type UseRevokeDistributionOptions = {
  mutationConfig?: MutationConfig<typeof revokeDistribution>;
};

export const useRevokeDistribution = ({
  mutationConfig,
}: UseRevokeDistributionOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: DISTRIBUTIONS_KEY(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: revokeDistribution,
  });
};

type UseRevokeAllDistributionsOptions = {
  mutationConfig?: MutationConfig<typeof revokeAllDistributions>;
};

export const useRevokeAllDistributions = ({
  mutationConfig,
}: UseRevokeAllDistributionsOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: DISTRIBUTIONS_KEY(variables.songId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: revokeAllDistributions,
  });
};
