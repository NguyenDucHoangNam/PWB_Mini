import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig, QueryConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import type { CreateRoomInput, Room, RoomLookup, RoomStatus } from "../types";

export const LIVEROOM_ROOMS_KEY = "liveroom-rooms" as const;
export const roomKey = (roomId: string) => ["liveroom-rooms", roomId] as const;
export const roomLookupKey = (roomCode: string) =>
  ["liveroom-rooms", "by-code", roomCode] as const;

export interface ListRoomsParams {
  status?: RoomStatus;
  page?: number;
  size?: number;
}

export const createRoom = ({
  data,
}: {
  data: CreateRoomInput;
}): Promise<ApiResponse<Room>> =>
  apiClient.post(liveroomApi.rooms, data).then((res) => res.data);

export const listRooms = ({
  status,
  page,
  size,
}: ListRoomsParams): Promise<ApiResponse<PaginatedResponse<Room>>> =>
  apiClient
    .get(liveroomApi.rooms, { params: { status, page, size } })
    .then((res) => res.data);

export const getRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Room>> =>
  apiClient.get(liveroomApi.room(roomId)).then((res) => res.data);

export const findRoomByCode = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<RoomLookup>> =>
  apiClient.get(liveroomApi.roomByCode(roomCode)).then((res) => res.data);

export const endRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Room>> =>
  apiClient.post(liveroomApi.end(roomId)).then((res) => res.data);

export const undoEndRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Room>> =>
  apiClient.post(liveroomApi.undoEnd(roomId)).then((res) => res.data);

export const reopenRoom = ({ roomId }: { roomId: string }): Promise<ApiResponse<Room>> =>
  apiClient.post(liveroomApi.reopen(roomId)).then((res) => res.data);

type UseCreateRoomOptions = {
  mutationConfig?: MutationConfig<typeof createRoom>;
};

export const useCreateRoom = ({ mutationConfig }: UseCreateRoomOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [LIVEROOM_ROOMS_KEY] });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: createRoom,
  });
};

type UseListRoomsOptions = {
  queryConfig?: QueryConfig<typeof listRooms>;
};

export const useListRooms = ({
  status,
  page,
  size,
  queryConfig,
}: ListRoomsParams & UseListRoomsOptions) =>
  useQuery({
    queryKey: [LIVEROOM_ROOMS_KEY, { status, page, size }],
    queryFn: () => listRooms({ status, page, size }),
    placeholderData: keepPreviousData,
    ...queryConfig,
  });

type UseRoomOptions = {
  roomId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getRoom>;
};

export const useRoom = ({ roomId, enabled = true, queryConfig }: UseRoomOptions) =>
  useQuery({
    queryKey: roomKey(roomId),
    queryFn: () => getRoom({ roomId }),
    enabled: enabled && Boolean(roomId),
    ...queryConfig,
  });

type UseFindRoomByCodeOptions = {
  roomCode: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof findRoomByCode>;
};


export const useFindRoomByCode = ({
  roomCode,
  enabled = true,
  queryConfig,
}: UseFindRoomByCodeOptions) =>
  useQuery({
    queryKey: roomLookupKey(roomCode),
    queryFn: () => findRoomByCode({ roomCode }),
    enabled: enabled && Boolean(roomCode),
    staleTime: Infinity,
    gcTime: 60 * 60 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    refetchOnReconnect: false,
    ...queryConfig,
  });

function invalidateRoom(
  queryClient: ReturnType<typeof useQueryClient>,
  roomId: string,
): void {
  queryClient.invalidateQueries({ queryKey: [LIVEROOM_ROOMS_KEY] });
  queryClient.invalidateQueries({ queryKey: roomKey(roomId) });
}

type UseEndRoomOptions = {
  mutationConfig?: MutationConfig<typeof endRoom>;
};

export const useEndRoom = ({ mutationConfig }: UseEndRoomOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) invalidateRoom(queryClient, variables.roomId);
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: endRoom,
  });
};

type UseUndoEndRoomOptions = {
  mutationConfig?: MutationConfig<typeof undoEndRoom>;
};

export const useUndoEndRoom = ({ mutationConfig }: UseUndoEndRoomOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) invalidateRoom(queryClient, variables.roomId);
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: undoEndRoom,
  });
};

type UseReopenRoomOptions = {
  mutationConfig?: MutationConfig<typeof reopenRoom>;
};

export const useReopenRoom = ({ mutationConfig }: UseReopenRoomOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) invalidateRoom(queryClient, variables.roomId);
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: reopenRoom,
  });
};