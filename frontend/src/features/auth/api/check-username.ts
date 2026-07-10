import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { CheckUsernameResponse } from "../types";

export const checkUsername = (username: string): Promise<ApiResponse<CheckUsernameResponse>> => {
  return apiClient.get("/auth/check-username", { params: { q: username } }).then((res) => res.data);
};

type UseCheckUsernameOptions = {
  username: string;
  queryConfig?: QueryConfig<typeof checkUsername>;
};

export const useCheckUsername = ({ username, queryConfig }: UseCheckUsernameOptions) => {
  return useQuery({
    queryKey: ["check-username", username],
    queryFn: () => checkUsername(username),
    enabled: username.trim().length >= 3,
    staleTime: 5000, // cache availability for 5 seconds to reduce calls during active typing
    ...queryConfig,
  });
};
