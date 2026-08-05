"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Check, Copy, LogIn, RotateCcw, Square, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RoomStatusBadge } from "./room-status-badge";
import { formatRoomCode } from "../../utils/format-room-code";
import type { EndedReason, Room } from "../../types";

const ENDED_REASON_KEY: Record<EndedReason, string> = {
  MANUAL: "reasonManual",
  OWNER_GRACE_EXPIRED: "reasonGrace",
  EMPTY_TIMEOUT: "reasonEmpty",
  FORCE_ROLE_CHANGE: "reasonRoleChange",
};

interface RoomCardProps {
  room: Room;
  onEnd: (room: Room) => void;
  onReopen: (room: Room) => void;
}

export function RoomCard({ room, onEnd, onReopen }: RoomCardProps) {
  const t = useTranslations("liveroom.list");
  const tEnded = useTranslations("liveroom.room.ended");
  const [copied, setCopied] = useState(false);

  const isActive = room.status === "ACTIVE";

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(room.roomCode);
      setCopied(true);
      toast.success(t("copied"));
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(t("copyCode"));
    }
  };

  return (
    <div className="@container flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-4 md:p-5 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-black md:text-lg dark:text-white">
            {room.roomName}
          </h3>
          <p className="mt-1 flex items-center gap-1.5 text-xs text-neutral-500 md:text-sm dark:text-neutral-400">
            <Users className="size-3.5" aria-hidden />
            {t("participants", {
              count: room.currentParticipantCount,
              max: room.effectiveMaxParticipants,
            })}
          </p>
        </div>
        <RoomStatusBadge status={room.status} />
      </div>

      <div className="flex items-center gap-2">
        <span className="rounded-lg border border-neutral-200 bg-neutral-50 px-2.5 py-1.5 font-mono text-sm tracking-[0.2em] text-black dark:border-neutral-800 dark:bg-neutral-900 dark:text-white">
          {formatRoomCode(room.roomCode)}
        </span>
        <Button
          variant="ghost"
          size="icon"
          onClick={copyCode}
          aria-label={t("copyCode")}
          className="size-11 md:size-9"
        >
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        </Button>
      </div>

      {!isActive && room.endedAt ? (
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("endedAt", { date: new Date(room.endedAt).toLocaleString() })}
          {room.endedReason ? ` · ${tEnded(ENDED_REASON_KEY[room.endedReason])}` : ""}
        </p>
      ) : null}

      {room.reopenedCount > 0 ? (
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("reopenedCount", { count: room.reopenedCount })}
        </p>
      ) : null}

      <div className="mt-auto flex flex-col gap-2 @sm:flex-row">
        {isActive ? (
          <>
            <Link href={`/liveroom/${room.id}`} className="@sm:flex-1">
              <Button className="h-11 w-full md:h-9">
                <LogIn className="size-4" />
                {t("openRoom")}
              </Button>
            </Link>
            <Button
              variant="destructive"
              className="h-11 md:h-9 @sm:w-auto"
              onClick={() => onEnd(room)}
            >
              <Square className="size-4" />
              {t("end")}
            </Button>
          </>
        ) : (
          <Button
            variant="outline"
            className="h-11 w-full md:h-9"
            onClick={() => onReopen(room)}
          >
            <RotateCcw className="size-4" />
            {t("reopen")}
          </Button>
        )}
      </div>
    </div>
  );
}