import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import { liveroomApi } from "../lib/liveroom-destinations";
import type { RtcConfig } from "../types";

export const RTC_CONFIG_KEY = "liveroom-rtc-config" as const;
export const rtcConfigKey = (roomId: string) => ["liveroom-rtc-config", roomId] as const;

export const getRtcConfig = ({
  roomId,
}: {
  roomId: string;
}): Promise<ApiResponse<RtcConfig>> =>
  apiClient.get(liveroomApi.rtcConfig(roomId)).then((res) => res.data);

type UseRtcConfigOptions = {
  roomId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getRtcConfig>;
};

export const useRtcConfig = ({ roomId, enabled = true, queryConfig }: UseRtcConfigOptions) =>
  useQuery({
    queryKey: rtcConfigKey(roomId),
    queryFn: () => getRtcConfig({ roomId }),
    enabled: enabled && Boolean(roomId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    ...queryConfig,
  });