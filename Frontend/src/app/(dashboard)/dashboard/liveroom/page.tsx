"use client";

import { Spinner } from "@/components/ui/spinner";
import { LiveroomList } from "@/features/liveroom/components/room-list/liveroom-list";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function DashboardLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();

  if (resolving) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner size="sm" />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col font-sans">
      <LiveroomList />
    </div>
  );
}
