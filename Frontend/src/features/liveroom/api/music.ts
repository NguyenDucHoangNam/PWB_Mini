import { apiClient } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import type { RoomAudioUrl } from "../types";


export const getRoomAudioUrl = ({
  roomId,
}: {
  roomId: string;
}): Promise<ApiResponse<RoomAudioUrl>> =>
  apiClient.get(liveroomApi.audioUrl(roomId)).then((res) => res.data);