"use client";

import { useTranslations } from "next-intl";
import type { LiveRoomStatus } from "../types";

interface RoomStatusBadgeProps {
  status: LiveRoomStatus;
}

const STATUS_STYLES: Record<LiveRoomStatus, string> = {
  ACTIVE:
    "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
  PAUSED:
    "border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300",
  ENDED:
    "border-neutral-300 bg-neutral-50 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900/60 dark:text-neutral-300",
};

export function RoomStatusBadge({ status }: RoomStatusBadgeProps) {
  const t = useTranslations("liveroom.status");
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${STATUS_STYLES[status]}`}
    >
      {t(status.toLowerCase() as "active" | "paused" | "ended")}
    </span>
  );
}
