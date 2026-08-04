"use client";

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { PROFILE_KEY, useProfile } from "./profile";

export function useAvatarUrl() {
  const isAuthenticated = useAuthStore((state) => !!state.accessToken);
  const queryClient = useQueryClient();

  const { data } = useProfile({ queryConfig: { enabled: isAuthenticated } });

  const onImageError = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: [PROFILE_KEY] });
  }, [queryClient]);

  return {
    avatarUrl: data?.data?.avatarUrl ?? null,
    onImageError,
  };
}
