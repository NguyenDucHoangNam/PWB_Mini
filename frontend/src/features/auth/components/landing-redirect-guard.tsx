"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

export function LandingRedirectGuard() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());

  useEffect(() => {
    if (isAuthenticated) {
      router.replace("/dashboard");
    }
  }, [isAuthenticated, router]);

  return null;
}
