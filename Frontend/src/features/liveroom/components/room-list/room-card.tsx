"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Check, Copy, LogIn, RotateCcw, Square, Users } from "lucide-react";
import {
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
  NeuPanel,
  neuButton,
} from "@/components/ui/neu";
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
    <NeuPanel tone="tile" className="group flex flex-col justify-between gap-4 p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3
            className={`truncate text-base font-bold tracking-tight decoration-slate-400 underline-offset-4 group-hover:underline md:text-lg ${NEU_TEXT}`}
          >
            {room.roomName}
          </h3>
          <p
            className={`mt-2 flex items-center gap-1.5 text-xs font-medium md:text-sm ${NEU_TEXT_MUTED}`}
          >
            <Users className="size-3.5" aria-hidden="true" />
            {t("participants", {
              count: room.currentParticipantCount,
              max: room.effectiveMaxParticipants,
            })}
          </p>
        </div>
        <RoomStatusBadge status={room.status} />
      </div>

      <div className="flex items-center gap-3">
        <span
          className={`neu-pressed-sm rounded-xl border-none px-3.5 py-2 font-mono text-sm font-bold tracking-[0.2em] ${NEU_TEXT}`}
        >
          {formatRoomCode(room.roomCode)}
        </span>
        <NeuButton
          size="icon-sm"
          onClick={copyCode}
          aria-label={t("copyCode")}
          className={copied ? "text-indigo-600 dark:text-indigo-400" : ""}
        >
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        </NeuButton>
      </div>

      {!isActive && room.endedAt ? (
        <p className={`text-xs font-medium ${NEU_TEXT_MUTED}`}>
          {t("endedAt", { date: new Date(room.endedAt).toLocaleString() })}
          {room.endedReason ? ` · ${tEnded(ENDED_REASON_KEY[room.endedReason])}` : ""}
        </p>
      ) : null}

      {room.reopenedCount > 0 ? (
        <p className={`text-xs font-medium ${NEU_TEXT_MUTED}`}>
          {t("reopenedCount", { count: room.reopenedCount })}
        </p>
      ) : null}

      <div className="mt-auto flex flex-col gap-3 pt-2 sm:flex-row">
        {isActive ? (
          <>
            <Link
              href={`/liveroom/${room.id}`}
              className={neuButton({ variant: "primary" }, "w-full sm:flex-1")}
            >
              <LogIn className="size-4" aria-hidden="true" />
              {t("openRoom")}
            </Link>
            <NeuButton onClick={() => onEnd(room)}>
              <Square className="size-4" aria-hidden="true" />
              {t("end")}
            </NeuButton>
          </>
        ) : (
          <NeuButton className="w-full" onClick={() => onReopen(room)}>
            <RotateCcw className="size-4" aria-hidden="true" />
            {t("reopen")}
          </NeuButton>
        )}
      </div>
    </NeuPanel>
  );
}