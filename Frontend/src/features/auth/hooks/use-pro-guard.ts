"use client";

import { useAuthStore } from "@/features/auth/stores/use-auth-store";

export function useProGuard(): { isPro: boolean } {
  const user = useAuthStore((state) => state.user);
  return { isPro: user?.role === "PRO" };
}
