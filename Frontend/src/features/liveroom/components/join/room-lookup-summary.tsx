"use client";

import { useTranslations } from "next-intl";
import { Music, Users } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuPanel } from "@/components/ui/neu";
import { formatRoomCode } from "../../utils/format-room-code";
import type { RoomLookup } from "../../types";

export function RoomLookupSummary({ lookup }: { lookup: RoomLookup }) {
  const t = useTranslations("liveroom.join");

  return (
    <NeuPanel className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="neu-pressed-sm grid size-11 shrink-0 place-items-center rounded-2xl border-none text-indigo-600 dark:text-indigo-400">
            <Music className="size-4" aria-hidden />
          </div>
          <div className="min-w-0">
            <h2 className={`truncate text-base font-bold tracking-tight ${NEU_TEXT}`}>
              {lookup.roomName}
            </h2>
            <p className={`mt-1 flex items-center gap-1.5 text-xs font-medium ${NEU_TEXT_MUTED}`}>
              <Users className="size-3.5" aria-hidden />
              {t("capacityLine", {
                count: lookup.currentParticipantCount,
                max: lookup.maxParticipants,
              })}
            </p>
          </div>
        </div>
        <span
          className={`neu-pressed-sm shrink-0 rounded-xl border-none px-3 py-2 font-mono text-sm font-bold tracking-[0.2em] ${NEU_TEXT}`}
        >
          {formatRoomCode(lookup.roomCode)}
        </span>
      </div>

      {lookup.full ? (
        <p
          className={`neu-pressed-sm rounded-xl border-none px-3.5 py-2.5 text-sm font-semibold ${NEU_TEXT}`}
        >
          {t("full")}
        </p>
      ) : null}
    </NeuPanel>
  );
}
