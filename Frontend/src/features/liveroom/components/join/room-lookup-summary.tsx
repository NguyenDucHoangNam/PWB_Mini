"use client";

import { useTranslations } from "next-intl";
import { Music, Users } from "lucide-react";
import { formatRoomCode } from "../../utils/format-room-code";
import type { RoomLookup } from "../../types";

export function RoomLookupSummary({ lookup }: { lookup: RoomLookup }) {
  const t = useTranslations("liveroom.join");

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 shadow-xs">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="grid size-10 shrink-0 place-items-center rounded-lg border border-border bg-muted text-muted-foreground">
            <Music className="size-4" aria-hidden />
          </div>
          <div className="min-w-0">
            <h2 className="truncate text-base font-bold tracking-tight text-foreground">
              {lookup.roomName}
            </h2>
            <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
              <Users className="size-3.5" aria-hidden />
              {t("capacityLine", {
                count: lookup.currentParticipantCount,
                max: lookup.maxParticipants,
              })}
            </p>
          </div>
        </div>
        <span className="shrink-0 rounded-lg border border-border bg-muted px-2.5 py-1.5 font-mono text-sm font-semibold tracking-[0.2em] text-foreground">
          {formatRoomCode(lookup.roomCode)}
        </span>
      </div>

      {lookup.full ? (
        <p className="rounded-lg border border-border bg-muted/50 px-3 py-2 text-sm font-medium text-foreground">
          {t("full")}
        </p>
      ) : null}
    </div>
  );
}