"use client";

import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { JoinRoomCard } from "@/features/liveroom/components/join-room-card";
import { ParticipantsList } from "@/features/liveroom/components/participants-list";
import { RoomModeBadge } from "@/features/liveroom/components/room-mode-badge";
import { RoomStatusBadge } from "@/features/liveroom/components/room-status-badge";
import { useCheckRoomExists, useRoom } from "@/features/liveroom/api/rooms";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

export default function ListenerLiveRoomPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";

  const tCard = useTranslations("liveroom.card");
  const tActions = useTranslations("liveroom.actions");
  const tErrors = useTranslations("liveroom.errors");
  const tExists = useTranslations("liveroom.existsCheck");
  const tNav = useTranslations("liveroom.nav");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);

  const { data: existsRes, isLoading: existsLoading, isError: existsError } =
    useCheckRoomExists({ roomCode });
  const { data: roomRes, isLoading: roomLoading, isError: roomError } = useRoom({
    roomCode,
    queryConfig: { enabled: Boolean(existsRes?.data?.exists) },
  });

  if (existsLoading || roomLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
        <span>{tExists("checking")}</span>
      </div>
    );
  }

  if (existsError || !existsRes?.data?.exists || roomError || !roomRes?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const room = roomRes.data;
  const isHost = currentUserId === room.hostUserId;
  const isActive = room.status === "ACTIVE";

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {room.title}
          </h1>
          <RoomStatusBadge status={room.status} />
          <RoomModeBadge mode={room.mode} />
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
          <span className="font-mono">{room.roomCode}</span>
          <span>
            {tCard("capacity", {
              current: room.currentParticipantCount,
              max: room.maxParticipants,
            })}
          </span>
        </div>
        {room.description && (
          <p className="text-sm text-neutral-600 dark:text-neutral-300 max-w-prose">
            {room.description}
          </p>
        )}
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {tNav("listenerSubtitle")}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="flex flex-col gap-3">
          <JoinRoomCard
            roomCode={room.roomCode}
            isActive={isActive}
            isHost={isHost}
          />
          {isHost && (
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              {tErrors("notHost")}
            </p>
          )}
        </div>
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
        </div>
      </div>
    </div>
  );
}
