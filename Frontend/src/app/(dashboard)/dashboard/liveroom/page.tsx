"use client";

import { Spinner } from "@/components/ui/spinner";
import { NeuScreen } from "@/components/ui/neu";
import { LiveroomList } from "@/features/liveroom/components/room-list/liveroom-list";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function DashboardLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();

  return (
    <NeuScreen className="font-sans">
      {resolving ? (
        <div className="flex min-h-[60vh] flex-1 items-center justify-center">
          <Spinner size="sm" />
        </div>
      ) : (
        <LiveroomList />
      )}
    </NeuScreen>
  );
}
