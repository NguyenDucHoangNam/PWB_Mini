"use client";

import { NeuScreen } from "@/components/ui/neu";
import { LiveroomList } from "@/features/liveroom/components/room-list/liveroom-list";

export default function DashboardLiveroomPage() {
  return (
    <NeuScreen className="font-sans">
      <LiveroomList />
    </NeuScreen>
  );
}
