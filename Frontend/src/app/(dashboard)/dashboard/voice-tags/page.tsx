"use client";

import { DashboardHeader } from "@/app/(dashboard)/dashboard/_components/dashboard-header";
import { DashboardVoiceTagsTab } from "@/features/voice/components/dashboard-voice-tags-tab";
import { NeuScreen } from "@/components/ui/neu";

export default function DashboardVoiceTagsPage() {
  return (
    <NeuScreen>
      <DashboardHeader />
      <DashboardVoiceTagsTab />
    </NeuScreen>
  );
}
