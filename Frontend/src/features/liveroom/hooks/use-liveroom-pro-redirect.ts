"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

export const LIVEROOM_JOIN_PATH = "/dashboard/liveroom/join";

export function useLiveroomProRedirect(): { isPro: boolean; resolving: boolean } {
  const router = useRouter();
  const { isPro } = useProGuard();
  const bootstrapping = useAuthStore((state) => state.bootstrapping);

  useEffect(() => {
    if (bootstrapping || isPro) return;
    router.replace(LIVEROOM_JOIN_PATH);
  }, [bootstrapping, isPro, router]);

  return { isPro, resolving: bootstrapping || !isPro };
}
