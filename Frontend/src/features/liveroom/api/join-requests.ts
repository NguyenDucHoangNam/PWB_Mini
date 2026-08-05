import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig, QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import type { CreateJoinRequestInput, JoinRequest } from "../types";

export const JOIN_REQUESTS_KEY = "liveroom-join-requests" as const;
export const pendingJoinRequestsKey = (roomId: string) =>
  ["liveroom-join-requests", roomId, "pending"] as const;
export const myJoinRequestKey = (roomId: string) =>
  ["liveroom-join-requests", roomId, "me"] as const;

export const createJoinRequest = ({
  roomId,
  data,
}: {
  roomId: string;
  data: CreateJoinRequestInput;
}): Promise<ApiResponse<JoinRequest>> =>
  apiClient.post(liveroomApi.joinRequests(roomId), data).then((res) => res.data);

export const listPendingJoinRequests = ({
  roomId,
}: {
  roomId: string;
}): Promise<ApiResponse<JoinRequest[]>> =>
  apiClient.get(liveroomApi.joinRequests(roomId)).then((res) => res.data);

export const getMyJoinRequest = ({
  roomId,
}: {
  roomId: string;
}): Promise<ApiResponse<JoinRequest>> =>
  apiClient.get(liveroomApi.myJoinRequest(roomId)).then((res) => res.data);

export const cancelJoinRequest = ({
  roomId,
  requestId,
}: {
  roomId: string;
  requestId: string;
}): Promise<ApiResponse<JoinRequest>> =>
  apiClient.delete(liveroomApi.joinRequest(roomId, requestId)).then((res) => res.data);

export const approveJoinRequest = ({
  roomId,
  requestId,
}: {
  roomId: string;
  requestId: string;
}): Promise<ApiResponse<JoinRequest>> =>
  apiClient.post(liveroomApi.approveJoinRequest(roomId, requestId)).then((res) => res.data);

export const rejectJoinRequest = ({
  roomId,
  requestId,
}: {
  roomId: string;
  requestId: string;
}): Promise<ApiResponse<JoinRequest>> =>
  apiClient.post(liveroomApi.rejectJoinRequest(roomId, requestId)).then((res) => res.data);

type UseCreateJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof createJoinRequest>;
};

export const useCreateJoinRequest = ({
  mutationConfig,
}: UseCreateJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.setQueryData(myJoinRequestKey(variables.roomId), response);
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: createJoinRequest,
  });
};

type UseMyJoinRequestOptions = {
  roomId: string;
  enabled?: boolean;
  refetchInterval?: number | false;
  queryConfig?: QueryConfig<typeof getMyJoinRequest>;
};

export const useMyJoinRequest = ({
  roomId,
  enabled = true,
  refetchInterval = false,
  queryConfig,
}: UseMyJoinRequestOptions) =>
  useQuery({
    queryKey: myJoinRequestKey(roomId),
    queryFn: () => getMyJoinRequest({ roomId }),
    enabled: enabled && Boolean(roomId),
    retry: false,
    refetchInterval,
    ...queryConfig,
  });

type UsePendingJoinRequestsOptions = {
  roomId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof listPendingJoinRequests>;
};

export const usePendingJoinRequests = ({
  roomId,
  enabled = true,
  queryConfig,
}: UsePendingJoinRequestsOptions) =>
  useQuery({
    queryKey: pendingJoinRequestsKey(roomId),
    queryFn: () => listPendingJoinRequests({ roomId }),
    enabled: enabled && Boolean(roomId),
    ...queryConfig,
  });

type UseCancelJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof cancelJoinRequest>;
};

export const useCancelJoinRequest = ({
  mutationConfig,
}: UseCancelJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.setQueryData(myJoinRequestKey(variables.roomId), response);
        queryClient.invalidateQueries({ queryKey: pendingJoinRequestsKey(variables.roomId) });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: cancelJoinRequest,
  });
};

type UseApproveJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof approveJoinRequest>;
  onCapacityRejected?: (request: JoinRequest) => void;
};


export const useApproveJoinRequest = ({
  mutationConfig,
  onCapacityRejected,
}: UseApproveJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      queryClient.invalidateQueries({ queryKey: pendingJoinRequestsKey(variables.roomId) });
      if (response.data?.state === "REJECTED_BY_CAPACITY") {
        onCapacityRejected?.(response.data);
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: approveJoinRequest,
  });
};

type UseRejectJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof rejectJoinRequest>;
};

export const useRejectJoinRequest = ({
  mutationConfig,
}: UseRejectJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      queryClient.invalidateQueries({ queryKey: pendingJoinRequestsKey(variables.roomId) });
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: rejectJoinRequest,
  });
};