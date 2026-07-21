"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { RoomModeBadge } from "./room-mode-badge";
import { RoomStatusBadge } from "./room-status-badge";
import type { LiveRoomSummary } from "../types";

interface RoomCardProps {
  room: LiveRoomSummary;
  onEnd: (room: LiveRoomSummary) => void;
}

function formatDate(value: string | null) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(d);
}

export function RoomCard({ room, onEnd }: RoomCardProps) {
  const tCard = useTranslations("liveroom.card");

  const isEnded = room.status === "ENDED";

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
      <div className="flex flex-col gap-2">
        <div className="flex items-start justify-between gap-2">
          <div className="flex flex-col gap-1 min-w-0">
            <Link
              href={`/dashboard/live-rooms/${room.roomCode}`}
              className="truncate text-base font-semibold text-black hover:underline dark:text-white"
            >
              {room.title}
            </Link>
            <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
              <RoomStatusBadge status={room.status} />
              <RoomModeBadge mode={room.mode} />
              <span className="font-mono">{room.roomCode}</span>
            </div>
          </div>
        </div>
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {tCard("capacity", { current: room.currentParticipantCount, max: room.maxParticipants })}
        </p>
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {tCard("createdAt", { date: formatDate(room.createdAt) })}
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={`/dashboard/live-rooms/${room.roomCode}`}>
          <Button variant="outline" size="sm">
            {tCard("manage")}
          </Button>
        </Link>
        {!isEnded && (
          <Button variant="destructive" size="sm" onClick={() => onEnd(room)}>
            {tCard("end")}
          </Button>
        )}
      </div>

      {!isEnded && null}
    </div>
  );
}
