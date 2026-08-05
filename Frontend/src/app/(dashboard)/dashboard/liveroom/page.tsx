"use client";

import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { LiveroomList } from "@/features/liveroom/components/room-list/liveroom-list";
import { LiveroomProPrompt } from "@/features/liveroom/components/room-list/liveroom-pro-prompt";

export default function DashboardLiveroomPage() {
  const { isPro } = useProGuard();

  if (!isPro) {
    return <LiveroomProPrompt />;
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <LiveroomList />
    </div>
  );
}