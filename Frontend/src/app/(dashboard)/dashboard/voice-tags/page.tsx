"use client";

import { DashboardHeader } from "@/app/(dashboard)/dashboard/_components/dashboard-header";
import { DashboardVoiceTagsTab } from "@/features/voice/components/dashboard-voice-tags-tab";
import { ProUpgradePrompt } from "@/features/voice/components/pro-upgrade-prompt";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";

export default function DashboardVoiceTagsPage() {
  const { isPro } = useProGuard();

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  return (
    <div className="flex flex-1 flex-col gap-5 sm:gap-6">
      <DashboardHeader />
      <DashboardVoiceTagsTab />
    </div>
  );
}
