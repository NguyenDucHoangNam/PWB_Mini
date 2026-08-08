"use client";

import { use } from "react";
import { RoomScreen } from "@/features/liveroom/components/room/room-screen";

export default function LiveroomRoomPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = use(params);
  return <RoomScreen roomId={roomId} />;
}