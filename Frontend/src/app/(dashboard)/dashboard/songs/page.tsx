"use client";

import { DashboardHeader } from "@/app/(dashboard)/dashboard/_components/dashboard-header";
import { DashboardSongsTab } from "@/features/voice/components/dashboard-songs-tab";
import { NeuScreen } from "@/components/ui/neu";

export default function DashboardSongsPage() {
  return (
    <NeuScreen>
      <DashboardHeader />
      <DashboardSongsTab />
    </NeuScreen>
  );
}
