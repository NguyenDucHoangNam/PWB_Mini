"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useProfile } from "@/features/auth/api/profile";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useAuthChannelSync } from "@/lib/use-auth-channel";
import { DashboardLayout } from "@/components/layout/dashboard-layout";
import { persistReturnTo } from "@/hooks/use-return-to";

export default function DashboardRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const setUser = useAuthStore((state) => state.setUser);
  const [isAuthChecked, setIsAuthChecked] = useState(false);

  // Cross-tab auth sync.
  useAuthChannelSync();

  // 1. Initial check for in-memory access token. Refresh-on-demand via
  // axios interceptor will recover a missing token from the httpOnly cookie.
  useEffect(() => {
    if (!accessToken) {
      persistReturnTo(window.location.pathname + window.location.search);
      router.replace("/login");
      return;
    }
    setIsAuthChecked(true);
  }, [accessToken, router]);

  // 2. Fetch profile to verify active status (e.g. pending deletion).
  const {
    data: profileData,
    isLoading,
    isError,
  } = useProfile({
    queryConfig: {
      enabled: isAuthChecked,
      retry: false,
    },
  });

  // 3. Handle redirection based on profile API response.
  useEffect(() => {
    if (isAuthChecked && !isLoading && profileData?.data) {
      if (profileData.data.status === "PENDING_DELETION") {
        router.replace("/account-recovery");
        return;
      }
      // Keep store user in sync with the latest profile payload.
      const storeUser = useAuthStore.getState().user;
      if (
        !storeUser ||
        storeUser.email !== profileData.data.email ||
        storeUser.fullName !== profileData.data.fullName
      ) {
        setUser({
          username: profileData.data.username,
          email: profileData.data.email,
          fullName: profileData.data.fullName,
          status: profileData.data.status,
          role: profileData.data.role,
          oauthProvider: profileData.data.oauthProvider,
        });
      }
    }
  }, [isAuthChecked, isLoading, profileData, router, setUser]);

  // 4. If the profile fetch fails, treat as logged out.
  useEffect(() => {
    if (isAuthChecked && isError) {
      useAuthStore.getState().clearAuth();
      router.replace("/login");
    }
  }, [isAuthChecked, isError, router]);

  if (!isAuthChecked || isLoading) {
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

  if (isError || profileData?.data?.status === "PENDING_DELETION") {
    return null;
  }

  return <DashboardLayout>{children}</DashboardLayout>;
}
