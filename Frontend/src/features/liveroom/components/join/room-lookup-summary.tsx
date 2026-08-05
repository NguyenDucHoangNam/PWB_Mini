"use client";

import { useTranslations } from "next-intl";
import { Users } from "lucide-react";
import { formatRoomCode } from "../../utils/format-room-code";
import type { RoomLookup } from "../../types";

export function RoomLookupSummary({ lookup }: { lookup: RoomLookup }) {
  const t = useTranslations("liveroom.join");

  const ended = lookup.status === "ENDED";

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 md:p-5 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-semibold text-black dark:text-white">
            {lookup.roomName}
          </h2>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-neutral-500 dark:text-neutral-400">
            <Users className="size-3.5" aria-hidden />
            {t("capacityLine", {
              count: lookup.currentParticipantCount,
              max: lookup.maxParticipants,
            })}
          </p>
        </div>
        <span className="rounded-lg border border-neutral-200 bg-neutral-50 px-2.5 py-1.5 font-mono text-sm tracking-[0.2em] text-black dark:border-neutral-800 dark:bg-neutral-900 dark:text-white">
          {formatRoomCode(lookup.roomCode)}
        </span>
      </div>

      {ended ? (
        <p className="text-sm text-red-600 dark:text-red-400">{t("ended")}</p>
      ) : lookup.full ? (
        <p className="text-sm text-amber-700 dark:text-amber-400">{t("full")}</p>
      ) : null}
    </div>
  );
}