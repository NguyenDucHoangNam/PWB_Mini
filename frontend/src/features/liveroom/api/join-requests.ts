import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveRoomKey } from "./rooms";
import { liveRoomParticipantsKey } from "./participants";
import type {
  CreateJoinRequestBody,
  JoinRequestDecisionBody,
  JoinRequestStatus,
  LiveRoomJoinRequest,
} from "../types";

export const LIVE_ROOM_JOIN_REQUESTS_KEY = "live-room-join-requests" as const;
export const liveRoomJoinRequestsKey = (roomCode: string, status?: JoinRequestStatus) =>
  status
    ? (["live-room-join-requests", roomCode, status] as const)
    : (["live-room-join-requests", roomCode] as const);

export const createJoinRequest = ({
  roomCode,
  data,
}: {
  roomCode: string;
  data?: CreateJoinRequestBody;
}): Promise<ApiResponse<LiveRoomJoinRequest>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/join-requests`, data ?? {})
    .then((res) => res.data);

export const listJoinRequests = ({
  roomCode,
  status,
}: {
  roomCode: string;
  status?: JoinRequestStatus;
}): Promise<ApiResponse<LiveRoomJoinRequest[]>> =>
  apiClient
    .get(`/live-rooms/${roomCode}/join-requests`, { params: { status } })
    .then((res) => res.data);

export const approveJoinRequest = ({
  roomCode,
  requestId,
  data,
}: {
  roomCode: string;
  requestId: string;
  data?: JoinRequestDecisionBody;
}): Promise<ApiResponse<LiveRoomJoinRequest>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/join-requests/${requestId}/approve`, data ?? {})
    .then((res) => res.data);

export const rejectJoinRequest = ({
  roomCode,
  requestId,
  data,
}: {
  roomCode: string;
  requestId: string;
  data?: JoinRequestDecisionBody;
}): Promise<ApiResponse<LiveRoomJoinRequest>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/join-requests/${requestId}/reject`, data ?? {})
    .then((res) => res.data);

export const cancelJoinRequest = ({
  roomCode,
  requestId,
}: {
  roomCode: string;
  requestId: string;
}): Promise<ApiResponse<void>> =>
  apiClient.delete(`/live-rooms/${roomCode}/join-requests/${requestId}`).then((res) => res.data);

type UseCreateJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof createJoinRequest>;
};

export const useCreateJoinRequest = ({ mutationConfig }: UseCreateJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomJoinRequestsKey(variables.roomCode),
        });
      }
    },
    ...mutationConfig,
    mutationFn: createJoinRequest,
  });
};

type UseListJoinRequestsParams = {
  roomCode: string;
  status?: JoinRequestStatus;
  queryConfig?: QueryConfig<typeof listJoinRequests>;
};

export const useListJoinRequests = ({
  roomCode,
  status,
  queryConfig,
}: UseListJoinRequestsParams) =>
  useQuery({
    queryKey: liveRoomJoinRequestsKey(roomCode, status),
    queryFn: () => listJoinRequests({ roomCode, status }),
    enabled: Boolean(roomCode),
    ...queryConfig,
  });

type UseApproveJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof approveJoinRequest>;
};

export const useApproveJoinRequest = ({ mutationConfig }: UseApproveJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomJoinRequestsKey(variables.roomCode),
        });
        queryClient.invalidateQueries({
          queryKey: liveRoomParticipantsKey(variables.roomCode),
        });
        queryClient.invalidateQueries({ queryKey: liveRoomKey(variables.roomCode) });
      }
    },
    ...mutationConfig,
    mutationFn: approveJoinRequest,
  });
};

type UseRejectJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof rejectJoinRequest>;
};

export const useRejectJoinRequest = ({ mutationConfig }: UseRejectJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomJoinRequestsKey(variables.roomCode),
        });
      }
    },
    ...mutationConfig,
    mutationFn: rejectJoinRequest,
  });
};

type UseCancelJoinRequestOptions = {
  mutationConfig?: MutationConfig<typeof cancelJoinRequest>;
};

export const useCancelJoinRequest = ({ mutationConfig }: UseCancelJoinRequestOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomJoinRequestsKey(variables.roomCode),
        });
      }
    },
    ...mutationConfig,
    mutationFn: cancelJoinRequest,
  });
};
