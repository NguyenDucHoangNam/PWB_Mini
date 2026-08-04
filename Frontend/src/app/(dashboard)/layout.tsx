"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useAuthChannelSync } from "@/lib/use-auth-channel";
import { DashboardLayout } from "@/components/layout/dashboard-layout";
import { persistReturnTo } from "@/hooks/use-return-to";

const subscribeAuthStore = (callback: () => void) =>
  useAuthStore.subscribe(callback);

const getAccessTokenSnapshot = () => useAuthStore.getState().accessToken ?? null;
const getServerAccessTokenSnapshot = () => null;

export default function DashboardRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const bootstrapping = useAuthStore((state) => state.bootstrapping);
  const accessToken = useSyncExternalStore(
    subscribeAuthStore,
    getAccessTokenSnapshot,
    getServerAccessTokenSnapshot,
  );

  useAuthChannelSync();

  useEffect(() => {
    if (bootstrapping) return;
    if (!accessToken) {
      persistReturnTo(window.location.pathname + window.location.search);
      router.replace("/login");
    }
  }, [accessToken, bootstrapping, router]);

  if (bootstrapping || !accessToken) {
    if (bootstrapping || !accessToken) {
      return (
        <div className="flex h-screen w-screen items-center justify-center bg-white dark:bg-black">
          <svg className="animate-spin size-8 text-neutral-500" fill="none" viewBox="0 0 24 24">
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
    return null;
  }

  return <DashboardLayout>{children}</DashboardLayout>;
}
