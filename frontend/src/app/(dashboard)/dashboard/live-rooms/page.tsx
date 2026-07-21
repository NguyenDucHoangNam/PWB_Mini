"use client";

import { useTranslations } from "next-intl";
import { DashboardLiveRoomsTab } from "@/features/liveroom/components/dashboard-live-rooms-tab";
import { ProUpgradePrompt } from "@/features/liveroom/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function DashboardLiveRoomsPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("liveroom.nav");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>
      <DashboardLiveRoomsTab />
    </div>
  );
}
