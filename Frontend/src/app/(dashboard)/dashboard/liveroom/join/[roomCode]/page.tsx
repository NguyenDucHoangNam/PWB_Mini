"use client";

import { use } from "react";
import { JoinFlow } from "@/features/liveroom/components/join/join-flow";
import { normalizeRoomCode } from "@/features/liveroom/utils/format-room-code";

export default function JoinLiveroomByCodePage({
  params,
}: {
  params: Promise<{ roomCode: string }>;
}) {
  const { roomCode } = use(params);

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="mx-auto w-full max-w-lg">
        <JoinFlow roomCode={normalizeRoomCode(roomCode)} />
      </div>
    </div>
  );
}