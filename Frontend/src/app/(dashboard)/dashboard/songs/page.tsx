"use client";

import { DashboardHeader } from "@/app/(dashboard)/dashboard/_components/dashboard-header";
import { DashboardSongsTab } from "@/features/voice/components/dashboard-songs-tab";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { NeuScreen } from "@/components/ui/neu";

export default function DashboardSongsPage() {
  const { isPro } = useProGuard();

  return (
    <NeuScreen>
      {isPro ? (
        <>
          <DashboardHeader />
          <DashboardSongsTab />
        </>
      ) : (
        <ProUpgradePrompt />
      )}
    </NeuScreen>
  );
}
