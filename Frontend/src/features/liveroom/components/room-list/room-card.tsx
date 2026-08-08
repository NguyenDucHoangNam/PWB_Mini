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
    <div className="key-press group relative flex flex-col justify-between gap-4 overflow-hidden rounded-xl border border-border bg-card p-5 shadow-xs beat-8th transition-colors ease-hammer hover:border-foreground/25 hover:shadow-sm">
      <div
        className={`absolute left-0 top-0 h-full w-1 beat-16th transition-colors ease-hammer ${
          isActive ? "bg-foreground" : "bg-border group-hover:bg-muted-foreground"
        }`}
        aria-hidden="true"
      />

      <div className="flex items-start justify-between gap-3 pl-1">
        <div className="min-w-0">
          <h3 className="truncate text-base font-bold tracking-tight text-foreground decoration-muted-foreground/40 underline-offset-4 group-hover:underline md:text-lg">
            {room.roomName}
          </h3>
          <p className="mt-1.5 flex items-center gap-1.5 text-xs text-muted-foreground md:text-sm">
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
        <span className="rounded-lg border border-border bg-muted px-3 py-1.5 font-mono text-sm font-semibold tracking-[0.2em] text-foreground">
          {formatRoomCode(room.roomCode)}
        </span>
        <Button
          variant="outline"
          size="icon"
          onClick={copyCode}
          aria-label={t("copyCode")}
          className="size-9 min-h-[44px] sm:min-h-0"
        >
          {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
        </Button>
      </div>

      {!isActive && room.endedAt ? (
        <p className="pl-1 text-xs text-muted-foreground">
          {t("endedAt", { date: new Date(room.endedAt).toLocaleString() })}
          {room.endedReason ? ` · ${tEnded(ENDED_REASON_KEY[room.endedReason])}` : ""}
        </p>
      ) : null}

      {room.reopenedCount > 0 ? (
        <p className="pl-1 text-xs text-muted-foreground">
          {t("reopenedCount", { count: room.reopenedCount })}
        </p>
      ) : null}

      <div className="mt-auto flex flex-col gap-2 pt-2 pl-1 sm:flex-row">
        {isActive ? (
          <>
            <Link href={`/liveroom/${room.id}`} className="sm:flex-1">
              <Button className="h-9 min-h-[44px] w-full font-semibold sm:min-h-0">
                <LogIn className="size-4" />
                {t("openRoom")}
              </Button>
            </Link>
            <Button
              variant="outline"
              className="h-9 min-h-[44px] font-semibold sm:min-h-0"
              onClick={() => onEnd(room)}
            >
              <Square className="size-4" />
              {t("end")}
            </Button>
          </>
        ) : (
          <Button
            variant="outline"
            className="h-9 min-h-[44px] w-full font-semibold sm:min-h-0"
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