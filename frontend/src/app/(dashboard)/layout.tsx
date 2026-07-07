"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useProfile } from "@/features/auth/api/profile";
import { DashboardLayout } from "@/components/layout/dashboard-layout";
import { toast } from "sonner";

export default function DashboardRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const t = useTranslations("errors.401");
  const [isTokenChecked, setIsTokenChecked] = useState(false);

  // 1. Initial check for access token existence to prevent flashes
  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      toast.info(t("desc"));
      router.replace("/login");
    } else {
      setIsTokenChecked(true);
    }
  }, [router, t]);

  // 2. Fetch the profile details to check active status (e.g. pending deletion)
  const { data: profileData, isLoading, isError } = useProfile({
    queryConfig: {
      enabled: isTokenChecked,
      retry: false,
    },
  });

  // 3. Handle redirection based on profile API response
  useEffect(() => {
    if (isTokenChecked && !isLoading) {
      if (isError) {
        localStorage.removeItem("accessToken");
        router.replace("/login");
        return;
      }

      if (profileData && profileData.data) {
        const userStatus = profileData.data.status;
        if (userStatus === "PENDING_DELETION") {
          router.replace("/account-recovery");
        }
      }
    }
  }, [isTokenChecked, isLoading, isError, profileData, router]);

  if (!isTokenChecked || isLoading) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-white dark:bg-black">
        <svg
          className="animate-spin size-8 text-neutral-500"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      </div>
    );
  }

  if (isError || (profileData?.data?.status === "PENDING_DELETION")) {
    return null; // layout redirection handling is in progress
  }

  return <DashboardLayout>{children}</DashboardLayout>;
}
