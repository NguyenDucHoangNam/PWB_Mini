import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import { CHAT_HISTORY_MAX_SIZE, type ChatHistory } from "../types";

export const CHAT_KEY = "liveroom-chat" as const;
export const chatHistoryKey = (roomId: string) => ["liveroom-chat", roomId] as const;

export const getChatHistory = ({
  roomId,
  cursor,
  size = CHAT_HISTORY_MAX_SIZE,
}: {
  roomId: string;
  cursor?: string;
  size?: number;
}): Promise<ApiResponse<ChatHistory>> =>
  apiClient
    .get(liveroomApi.chatMessages(roomId), { params: { cursor, size } })
    .then((res) => res.data);

type UseChatHistoryOptions = {
  roomId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getChatHistory>;
};


export const useChatHistory = ({
  roomId,
  enabled = true,
  queryConfig,
}: UseChatHistoryOptions) =>
  useQuery({
    queryKey: chatHistoryKey(roomId),
    queryFn: () => getChatHistory({ roomId }),
    enabled: enabled && Boolean(roomId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    ...queryConfig,
  });