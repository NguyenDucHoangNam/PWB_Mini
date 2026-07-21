import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveRoomKey } from "./rooms";
import type {
  JoinLiveRoomRequest,
  LiveRoomJoinResponse,
  ParticipantSummary,
} from "../types";

export const LIVE_ROOM_PARTICIPANTS_KEY = "live-room-participants" as const;
export const liveRoomParticipantsKey = (roomCode: string) =>
  ["live-room-participants", roomCode] as const;

export const joinRoom = ({
  roomCode,
  data,
}: {
  roomCode: string;
  data?: JoinLiveRoomRequest;
}): Promise<ApiResponse<LiveRoomJoinResponse>> =>
  apiClient.post(`/live-rooms/${roomCode}/join`, data ?? {}).then((res) => res.data);

export const leaveRoom = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<void>> =>
  apiClient.post(`/live-rooms/${roomCode}/leave`).then((res) => res.data);

export const listParticipants = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<ParticipantSummary[]>> =>
  apiClient.get(`/live-rooms/${roomCode}/participants`).then((res) => res.data);

type UseJoinRoomOptions = {
  mutationConfig?: MutationConfig<typeof joinRoom>;
};

export const useJoinRoom = ({ mutationConfig }: UseJoinRoomOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomParticipantsKey(variables.roomCode),
        });
        queryClient.invalidateQueries({ queryKey: liveRoomKey(variables.roomCode) });
      }
    },
    ...mutationConfig,
    mutationFn: joinRoom,
  });
};

type UseLeaveRoomOptions = {
  mutationConfig?: MutationConfig<typeof leaveRoom>;
};

export const useLeaveRoom = ({ mutationConfig }: UseLeaveRoomOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({
          queryKey: liveRoomParticipantsKey(variables.roomCode),
        });
        queryClient.invalidateQueries({ queryKey: liveRoomKey(variables.roomCode) });
      }
    },
    ...mutationConfig,
    mutationFn: leaveRoom,
  });
};

type UseParticipantsOptions = {
  queryConfig?: QueryConfig<typeof listParticipants>;
};

void ({} as UseParticipantsOptions);

export const useParticipants = ({
  roomCode,
  queryConfig,
}: {
  roomCode: string;
  queryConfig?: QueryConfig<typeof listParticipants>;
}) =>
  useQuery({
    queryKey: liveRoomParticipantsKey(roomCode),
    queryFn: () => listParticipants({ roomCode }),
    enabled: Boolean(roomCode),
    ...queryConfig,
  });
