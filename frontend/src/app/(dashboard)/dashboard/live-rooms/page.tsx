"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DashboardLiveRoomsTab } from "@/features/liveroom/components/dashboard-live-rooms-tab";
import { JoinRoomByCodeCard } from "@/features/liveroom/components/join-room-by-code-card";
import { ProUpgradePrompt } from "@/features/liveroom/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function DashboardLiveRoomsPage() {
  const { isPro } = useProGuard();
  const t = useTranslations("liveroom.nav");
  const tActions = useTranslations("liveroom.list");

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("title")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {t("subtitle")}
          </p>
        </div>
        <Link href="/dashboard/live-rooms/new">
          <Button>
            <Plus className="mr-1 inline size-4" />
            {tActions("createBtn")}
          </Button>
        </Link>
      </div>
      <JoinRoomByCodeCard />
      <DashboardLiveRoomsTab />
    </div>
  );
}
