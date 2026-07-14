import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  DistributionListItem,
  DistributeDemoRequest,
  DistributeDemoResponse,
} from "../types";
import { DEMOS_KEY } from "./audio";

export const DISTRIBUTIONS_KEY = (demoId: string) =>
  ["audio-distributions", demoId] as const;
export const DEMO_SUMMARIES_KEY = "audio-demo-summaries" as const;

export const getDistributions = ({
  demoId,
  page,
  size,
  includeRevoked,
}: {
  demoId: string;
  page: number;
  size: number;
  includeRevoked: boolean;
}): Promise<ApiResponse<PaginatedResponse<DistributionListItem>>> => {
  return apiClient
    .get(`/demos/${demoId}/distributions`, {
      params: { page, size, includeRevoked },
    })
    .then((res) => res.data);
};

export const distributeDemo = ({
  demoId,
  data,
}: {
  demoId: string;
  data: DistributeDemoRequest;
}): Promise<ApiResponse<DistributeDemoResponse>> => {
  return apiClient
    .post(`/demos/${demoId}/distribute`, data)
    .then((res) => res.data);
};

export const revokeDistribution = ({
  demoId,
  distributionId,
}: {
  demoId: string;
  distributionId: string;
}): Promise<ApiResponse<DistributionListItem>> => {
  return apiClient
    .delete(`/demos/${demoId}/distributions/${distributionId}`)
    .then((res) => res.data);
};

export const revokeAllDistributions = ({
  demoId,
}: {
  demoId: string;
}): Promise<ApiResponse<number>> => {
  return apiClient
    .delete(`/demos/${demoId}/distributions`)
    .then((res) => res.data);
};

export const suggestRecipients = ({
  q,
}: {
  q: string;
}): Promise<ApiResponse<string[]>> => {
  return apiClient
    .get("/demos/recipients/suggest", { params: { q } })
    .then((res) => res.data);
};

export function useDistributions({
  demoId,
  page,
  size,
  includeRevoked,
  queryConfig,
}: {
  demoId: string;
  page: number;
  size: number;
  includeRevoked: boolean;
  queryConfig?: QueryConfig<typeof getDistributions>;
}) {
  return useQuery({
    queryKey: DISTRIBUTIONS_KEY(demoId),
    queryFn: () => getDistributions({ demoId, page, size, includeRevoked }),
    enabled: Boolean(demoId),
    ...queryConfig,
  });
}

type UseDistributeDemoOptions = {
  mutationConfig?: MutationConfig<typeof distributeDemo>;
};

export const useDistributeDemo = ({
  mutationConfig,
}: UseDistributeDemoOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: DISTRIBUTIONS_KEY(variables.demoId),
        });
        queryClient.invalidateQueries({ queryKey: [DEMOS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: distributeDemo,
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
          queryKey: DISTRIBUTIONS_KEY(variables.demoId),
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
          queryKey: DISTRIBUTIONS_KEY(variables.demoId),
        });
      }
    },
    ...mutationConfig,
    mutationFn: revokeAllDistributions,
  });
};
