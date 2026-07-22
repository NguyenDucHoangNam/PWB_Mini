import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveRoomKey } from "./rooms";
import type { MediaStateUpdateBody, ParticipantSummary } from "../types";

export const LIVE_ROOM_PARTICIPANTS_KEY = "live-room-participants" as const;
export const liveRoomParticipantsKey = (roomCode: string) =>
  ["live-room-participants", roomCode] as const;

export const joinPublicRoom = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<ParticipantSummary>> =>
  apiClient.post(`/live-rooms/${roomCode}/join`).then((res) => res.data);

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

export const updateMyMedia = ({
  roomCode,
  body,
}: {
  roomCode: string;
  body: MediaStateUpdateBody;
}): Promise<ApiResponse<ParticipantSummary>> =>
  apiClient
    .patch(`/live-rooms/${roomCode}/participants/me/media`, body)
    .then((res) => res.data);

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

type UseUpdateMyMediaOptions = {
  mutationConfig?: MutationConfig<typeof updateMyMedia>;
};

export const useUpdateMyMedia = ({ mutationConfig }: UseUpdateMyMediaOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (!response.success) return;
      const updated = response.data;
      if (!updated) return;
      queryClient.setQueryData(
        liveRoomParticipantsKey(variables.roomCode),
        (old: unknown) => {
          if (!old || typeof old !== "object") return old;
          const list = ((old as { data?: ParticipantSummary[] }).data ?? []).slice();
          const index = list.findIndex((p) => p.userId === updated.userId);
          if (index >= 0) list[index] = updated;
          else list.push(updated);
          return { ...(old as Record<string, unknown>), data: list };
        },
      );
    },
    ...mutationConfig,
    mutationFn: updateMyMedia,
  });
};

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
