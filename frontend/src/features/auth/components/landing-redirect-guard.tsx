"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export function LandingRedirectGuard() {
  const router = useRouter();

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      router.replace("/dashboard");
    }
  }, [router]);

  return null;
}
