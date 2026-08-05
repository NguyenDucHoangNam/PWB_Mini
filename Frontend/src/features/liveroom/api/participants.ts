import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig, QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import type {
  ModerateParticipantInput,
  Participant,
  UpdateMediaStateInput,
} from "../types";

export const PARTICIPANTS_KEY = "liveroom-participants" as const;
export const participantsKey = (roomId: string) =>
  ["liveroom-participants", roomId] as const;

export const joinRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Participant>> =>
  apiClient.post(liveroomApi.me(roomId)).then((res) => res.data);

export const leaveRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Participant>> =>
  apiClient.delete(liveroomApi.me(roomId)).then((res) => res.data);

export const listParticipants = ({
  roomId,
}: {
  roomId: string;
}): Promise<ApiResponse<Participant[]>> =>
  apiClient.get(liveroomApi.participants(roomId)).then((res) => res.data);

export const updateMediaState = ({
  roomId,
  data,
}: {
  roomId: string;
  data: UpdateMediaStateInput;
}): Promise<ApiResponse<Participant>> =>
  apiClient.patch(liveroomApi.myMedia(roomId), data).then((res) => res.data);

export const kickParticipant = ({
  roomId,
  targetUserId,
  data,
}: {
  roomId: string;
  targetUserId: string;
  data?: ModerateParticipantInput;
}): Promise<ApiResponse<Participant>> =>
  apiClient.post(liveroomApi.kick(roomId, targetUserId), data ?? {}).then((res) => res.data);

export const muteParticipant = ({
  roomId,
  targetUserId,
  data,
}: {
  roomId: string;
  targetUserId: string;
  data?: ModerateParticipantInput;
}): Promise<ApiResponse<Participant>> =>
  apiClient.post(liveroomApi.mute(roomId, targetUserId), data ?? {}).then((res) => res.data);

type UseParticipantsOptions = {
  roomId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof listParticipants>;
};


export const useParticipants = ({
  roomId,
  enabled = true,
  queryConfig,
}: UseParticipantsOptions) =>
  useQuery({
    queryKey: participantsKey(roomId),
    queryFn: () => listParticipants({ roomId }),
    enabled: enabled && Boolean(roomId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    ...queryConfig,
  });

type UseJoinRoomOptions = { mutationConfig?: MutationConfig<typeof joinRoom> };

export const useJoinRoom = ({ mutationConfig }: UseJoinRoomOptions = {}) =>
  useMutation({ ...mutationConfig, mutationFn: joinRoom });

type UseLeaveRoomOptions = { mutationConfig?: MutationConfig<typeof leaveRoom> };

export const useLeaveRoom = ({ mutationConfig }: UseLeaveRoomOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      queryClient.removeQueries({ queryKey: participantsKey(variables.roomId) });
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: leaveRoom,
  });
};

type UseUpdateMediaStateOptions = {
  mutationConfig?: MutationConfig<typeof updateMediaState>;
};

export const useUpdateMediaState = ({
  mutationConfig,
}: UseUpdateMediaStateOptions = {}) =>
  useMutation({ ...mutationConfig, mutationFn: updateMediaState });

type UseKickParticipantOptions = {
  mutationConfig?: MutationConfig<typeof kickParticipant>;
};

export const useKickParticipant = ({ mutationConfig }: UseKickParticipantOptions = {}) =>
  useMutation({ ...mutationConfig, mutationFn: kickParticipant });

type UseMuteParticipantOptions = {
  mutationConfig?: MutationConfig<typeof muteParticipant>;
};

export const useMuteParticipant = ({ mutationConfig }: UseMuteParticipantOptions = {}) =>
  useMutation({ ...mutationConfig, mutationFn: muteParticipant });