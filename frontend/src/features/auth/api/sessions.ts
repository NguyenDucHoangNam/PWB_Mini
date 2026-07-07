import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { ActiveSessionResponse } from "../types";

export const getSessions = (): Promise<ApiResponse<ActiveSessionResponse[]>> => {
  return apiClient.get("/auth/sessions").then((res) => res.data);
};

export const revokeSession = ({
  tokenUuid,
}: {
  tokenUuid: string;
}): Promise<ApiResponse<void>> => {
  return apiClient.delete(`/auth/sessions/${tokenUuid}`).then((res) => res.data);
};

export const revokeAllOtherSessions = (): Promise<ApiResponse<void>> => {
  return apiClient.delete("/auth/sessions").then((res) => res.data);
};

type UseSessionsOptions = {
  queryConfig?: QueryConfig<typeof getSessions>;
};

export const useSessions = ({ queryConfig }: UseSessionsOptions = {}) => {
  return useQuery({
    queryKey: ["auth-sessions"],
    queryFn: getSessions,
    ...queryConfig,
  });
};

type UseRevokeSessionOptions = {
  mutationConfig?: MutationConfig<typeof revokeSession>;
};

export const useRevokeSession = ({ mutationConfig }: UseRevokeSessionOptions = {}) => {
  const queryClient = useQueryClient();

  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: ["auth-sessions"] });
      }
    },
    ...mutationConfig,
    mutationFn: revokeSession,
  });
};

type UseRevokeAllOtherSessionsOptions = {
  mutationConfig?: MutationConfig<typeof revokeAllOtherSessions>;
};

export const useRevokeAllOtherSessions = ({
  mutationConfig,
}: UseRevokeAllOtherSessionsOptions = {}) => {
  const queryClient = useQueryClient();

  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: ["auth-sessions"] });
      }
    },
    ...mutationConfig,
    mutationFn: revokeAllOtherSessions,
  });
};
