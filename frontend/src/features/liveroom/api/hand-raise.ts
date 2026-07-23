import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";

export type HandRaiseAction = "RAISE" | "LOWER";

export interface HandRaiseUpdateBody {
  action: HandRaiseAction;
}

export const broadcastHandRaise = ({
  roomCode,
  action,
}: {
  roomCode: string;
  action: HandRaiseAction;
}): Promise<ApiResponse<void>> =>
  apiClient
    .post(`/live-rooms/${roomCode}/hand-raise`, { action })
    .then((res) => res.data);

type UseUpdateMyHandRaiseOptions = {
  mutationConfig?: MutationConfig<typeof broadcastHandRaise>;
};

export const useUpdateMyHandRaise = ({
  mutationConfig,
}: UseUpdateMyHandRaiseOptions = {}) =>
  useMutation({
    ...mutationConfig,
    mutationFn: broadcastHandRaise,
  });
