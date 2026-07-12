import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type { CheckUsernameResponse } from "../types";

const CHECK_USERNAME_ENABLED = process.env.NEXT_PUBLIC_CHECK_USERNAME_ENABLED === "true";

export const checkUsername = (username: string): Promise<ApiResponse<CheckUsernameResponse>> => {
  return apiClient
    .get<ApiResponse<CheckUsernameResponse>>("/auth/check-username", { params: { username } })
    .then((res) => res.data);
};

type UseCheckUsernameOptions = {
  username: string;
  queryConfig?: QueryConfig<typeof checkUsername>;
};

export const useCheckUsername = ({ username, queryConfig }: UseCheckUsernameOptions) => {
  return useQuery({
    queryKey: ["check-username", username],
    queryFn: () => checkUsername(username),
    enabled: CHECK_USERNAME_ENABLED && username.trim().length >= 3,
    staleTime: 5000,
    ...queryConfig,
  });
};
