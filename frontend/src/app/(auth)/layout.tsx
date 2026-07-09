"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLayout } from "@/features/auth/components/auth-layout";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "@/lib/auth-refresh";

export default function AuthRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const [isChecking, setIsChecking] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      // If we already have a token in memory, redirect immediately.
      if (accessToken) {
        router.replace("/dashboard");
        return;
      }
      // Otherwise, attempt a silent refresh from the httpOnly cookie.
      try {
        await refreshAccessToken();
        if (cancelled) return;
        router.replace("/dashboard");
      } catch {
        if (cancelled) return;
        setIsChecking(false);
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [accessToken, router]);

  if (isChecking) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-white dark:bg-black">
        <svg
          className="animate-spin size-8 text-neutral-500"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
      </div>
    );
  }

  return <AuthLayout>{children}</AuthLayout>;
}