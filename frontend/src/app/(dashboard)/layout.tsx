"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { useProfile } from "@/features/auth/api/profile";
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
  const setUser = useAuthStore((state) => state.setUser);
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

  const {
    data: profileData,
    isLoading,
    isError,
  } = useProfile({
    queryConfig: {
      enabled: !!accessToken,
      retry: false,
    },
  });

  useEffect(() => {
    if (!accessToken || isLoading) return;
    if (isError) {
      useAuthStore.getState().clearAuth();
      router.replace("/login");
      return;
    }
    if (!profileData?.data) return;

    if (profileData.data.status === "PENDING_DELETION") {
      router.replace("/account-recovery");
      return;
    }

    const storeUser = useAuthStore.getState().user;
    if (
      !storeUser ||
      storeUser.email !== profileData.data.email ||
      storeUser.fullName !== profileData.data.fullName
    ) {
      setUser({
        username: profileData.data.username ?? storeUser?.username ?? "",
        email: profileData.data.email,
        fullName: profileData.data.fullName,
        status: profileData.data.status,
        role: profileData.data.role,
        oauthProvider: profileData.data.oauthProvider ?? storeUser?.oauthProvider ?? "LOCAL",
      });
    }
  }, [accessToken, isLoading, isError, profileData, router, setUser]);

  if (bootstrapping || !accessToken || isLoading || isError || profileData?.data?.status === "PENDING_DELETION") {
    if (bootstrapping) {
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
    if (!accessToken) return null;
    if (isError || profileData?.data?.status === "PENDING_DELETION") return null;
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

  return <DashboardLayout>{children}</DashboardLayout>;
}