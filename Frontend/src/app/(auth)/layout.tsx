"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthLayout } from "@/features/auth/components/auth-layout";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { refreshAccessToken } from "@/lib/auth-refresh";
import { Spinner } from "@/components/ui/spinner";

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
      if (accessToken) {
        router.replace("/");
        return;
      }
      try {
        await refreshAccessToken();
        if (cancelled) return;
        router.replace("/");
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
        <Spinner size="lg" />
      </div>
    );
  }

  return <AuthLayout>{children}</AuthLayout>;
}
