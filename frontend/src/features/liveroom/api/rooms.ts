import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  CreateLiveRoomRequest,
  LiveRoom,
  LiveRoomExistsResponse,
  LiveRoomSummary,
  LiveRoomViewerStatus,
  ListMyRoomsParams,
  UpdateLiveRoomSettingsRequest,
} from "../types";

export const LIVE_ROOMS_KEY = "live-rooms" as const;
export const liveRoomKey = (roomCode: string) => ["live-rooms", roomCode] as const;
export const liveRoomExistsKey = (roomCode: string) =>
  ["live-rooms", roomCode, "exists"] as const;
export const liveRoomViewerStatusKey = (roomCode: string) =>
  ["live-rooms", roomCode, "me-status"] as const;

export const createRoom = ({
  data,
}: {
  data: CreateLiveRoomRequest;
}): Promise<ApiResponse<LiveRoom>> =>
  apiClient.post("/live-rooms", data).then((res) => res.data);

export const listMyRooms = ({
  page,
  size,
  status,
}: ListMyRoomsParams): Promise<ApiResponse<PaginatedResponse<LiveRoomSummary>>> =>
  apiClient.get("/live-rooms/me", { params: { page, size, status } }).then((res) => res.data);

export const getRoom = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<LiveRoom>> =>
  apiClient.get(`/live-rooms/${roomCode}`).then((res) => res.data);

export const updateRoom = ({
  roomCode,
  data,
}: {
  roomCode: string;
  data: UpdateLiveRoomSettingsRequest;
}): Promise<ApiResponse<LiveRoom>> =>
  apiClient.patch(`/live-rooms/${roomCode}`, data).then((res) => res.data);

export const endRoom = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<void>> =>
  apiClient.post(`/live-rooms/${roomCode}/end`).then((res) => res.data);

export const checkRoomExists = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<LiveRoomExistsResponse>> =>
  apiClient.get(`/live-rooms/${roomCode}/exists`).then((res) => res.data);

export const getViewerStatus = ({
  roomCode,
}: {
  roomCode: string;
}): Promise<ApiResponse<LiveRoomViewerStatus>> =>
  apiClient.get(`/live-rooms/${roomCode}/me/status`).then((res) => res.data);

type UseCreateRoomOptions = {
  mutationConfig?: MutationConfig<typeof createRoom>;
};

export const useCreateRoom = ({ mutationConfig }: UseCreateRoomOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [LIVE_ROOMS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: createRoom,
  });
};

type UseMyRoomsOptions = {
  queryConfig?: QueryConfig<typeof listMyRooms>;
};

export const useMyRooms = ({
  page,
  size,
  status,
  queryConfig,
}: ListMyRoomsParams & UseMyRoomsOptions) =>
  useQuery({
    queryKey: [LIVE_ROOMS_KEY, "me", { page, size, status }],
    queryFn: () => listMyRooms({ page, size, status }),
    ...queryConfig,
  });

export const useRoom = ({
  roomCode,
  queryConfig,
}: {
  roomCode: string;
  queryConfig?: QueryConfig<typeof getRoom>;
}) =>
  useQuery({
    queryKey: liveRoomKey(roomCode),
    queryFn: () => getRoom({ roomCode }),
    enabled: Boolean(roomCode),
    ...queryConfig,
  });

type UseUpdateRoomOptions = {
  mutationConfig?: MutationConfig<typeof updateRoom>;
};

export const useUpdateRoom = ({ mutationConfig }: UseUpdateRoomOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [LIVE_ROOMS_KEY] });
        queryClient.invalidateQueries({ queryKey: liveRoomKey(variables.roomCode) });
      }
    },
    ...mutationConfig,
    mutationFn: updateRoom,
  });
};

type UseEndRoomOptions = {
  mutationConfig?: MutationConfig<typeof endRoom>;
};

export const useEndRoom = ({ mutationConfig }: UseEndRoomOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response, variables) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [LIVE_ROOMS_KEY] });
        queryClient.invalidateQueries({ queryKey: liveRoomKey(variables.roomCode) });
      }
    },
    ...mutationConfig,
    mutationFn: endRoom,
  });
};

export const useCheckRoomExists = ({
  roomCode,
  queryConfig,
}: {
  roomCode: string;
  queryConfig?: QueryConfig<typeof checkRoomExists>;
}) =>
  useQuery({
    queryKey: liveRoomExistsKey(roomCode),
    queryFn: () => checkRoomExists({ roomCode }),
    enabled: Boolean(roomCode),
    retry: false,
    ...queryConfig,
  });

export const useViewerStatus = ({
  roomCode,
  queryConfig,
}: {
  roomCode: string;
  queryConfig?: QueryConfig<typeof getViewerStatus>;
}) =>
  useQuery({
    queryKey: liveRoomViewerStatusKey(roomCode),
    queryFn: () => getViewerStatus({ roomCode }),
    enabled: Boolean(roomCode),
    retry: false,
    ...queryConfig,
  });
