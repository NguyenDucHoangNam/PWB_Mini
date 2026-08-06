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
    <div className="group relative flex flex-col justify-between gap-4 rounded-xl border border-neutral-300 bg-white p-5 shadow-xs transition-all hover:border-black dark:border-neutral-800 dark:bg-black dark:hover:border-white active:translate-y-[1px]">
      <div
        className={`absolute left-0 top-0 h-full w-[4px] rounded-l-xl transition-colors ${
          isActive
            ? "bg-black dark:bg-white"
            : "bg-neutral-300 dark:bg-neutral-700 group-hover:bg-black dark:group-hover:bg-white"
        }`}
        aria-hidden="true"
      />

      <div className="flex items-start justify-between gap-3 pl-1">
        <div className="min-w-0">
          <h3 className="truncate text-base font-bold tracking-tight text-neutral-900 md:text-lg dark:text-neutral-100 group-hover:underline underline-offset-4 decoration-neutral-400">
            {room.roomName}
          </h3>
          <p className="mt-1 flex items-center gap-1.5 font-mono text-xs text-neutral-500 md:text-sm dark:text-neutral-400">
            <Users className="size-3.5" aria-hidden="true" />
            {t("participants", {
              count: room.currentParticipantCount,
              max: room.effectiveMaxParticipants,
            })}
          </p>
        </div>
        <RoomStatusBadge status={room.status} />
      </div>

      <div className="flex items-center gap-2 pl-1">
        <span className="rounded-lg border border-neutral-300 bg-neutral-100 px-3 py-1.5 font-mono text-sm font-semibold tracking-[0.2em] text-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100">
          {formatRoomCode(room.roomCode)}
        </span>
        <Button
          variant="outline"
          size="icon"
          onClick={copyCode}
          aria-label={t("copyCode")}
          className="size-9 min-h-[44px] sm:min-h-0 border-neutral-300 dark:border-neutral-700 hover:bg-neutral-200 dark:hover:bg-neutral-800"
        >
          {copied ? <Check className="size-4 text-black dark:text-white" /> : <Copy className="size-4" />}
        </Button>
      </div>

      {!isActive && room.endedAt ? (
        <p className="pl-1 font-mono text-xs text-neutral-500 dark:text-neutral-400">
          {t("endedAt", { date: new Date(room.endedAt).toLocaleString() })}
          {room.endedReason ? ` · ${tEnded(ENDED_REASON_KEY[room.endedReason])}` : ""}
        </p>
      ) : null}

      {room.reopenedCount > 0 ? (
        <p className="pl-1 font-mono text-xs text-neutral-500 dark:text-neutral-400">
          {t("reopenedCount", { count: room.reopenedCount })}
        </p>
      ) : null}

      <div className="mt-auto flex flex-col gap-2 pt-2 sm:flex-row pl-1">
        {isActive ? (
          <>
            <Link href={`/liveroom/${room.id}`} className="sm:flex-1">
              <Button className="h-9 min-h-[44px] sm:min-h-0 w-full bg-black text-white hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200 font-semibold active:translate-y-[1px] transition-all">
                <LogIn className="size-4" />
                {t("openRoom")}
              </Button>
            </Link>
            <Button
              variant="outline"
              className="h-9 min-h-[44px] sm:min-h-0 border-neutral-300 dark:border-neutral-700 text-neutral-800 dark:text-neutral-200 hover:bg-neutral-200 dark:hover:bg-neutral-800 font-semibold"
              onClick={() => onEnd(room)}
            >
              <Square className="size-4" />
              {t("end")}
            </Button>
          </>
        ) : (
          <Button
            variant="outline"
            className="h-9 min-h-[44px] sm:min-h-0 w-full border-neutral-300 dark:border-neutral-700 text-neutral-800 dark:text-neutral-200 hover:bg-neutral-200 dark:hover:bg-neutral-800 font-semibold"
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