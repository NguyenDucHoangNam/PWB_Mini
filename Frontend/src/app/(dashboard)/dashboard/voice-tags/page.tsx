"use client";

import { DashboardHeader } from "@/app/(dashboard)/dashboard/_components/dashboard-header";
import { DashboardVoiceTagsTab } from "@/features/voice/components/dashboard-voice-tags-tab";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { NeuScreen } from "@/components/ui/neu";

export default function DashboardVoiceTagsPage() {
  const { isPro } = useProGuard();

  return (
    <NeuScreen>
      {isPro ? (
        <>
          <DashboardHeader />
          <DashboardVoiceTagsTab />
        </>
      ) : (
        <ProUpgradePrompt />
      )}
    </NeuScreen>
  );
}
