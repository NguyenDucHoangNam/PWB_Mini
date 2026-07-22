"use client";

import { useTranslations } from "next-intl";
import { Globe } from "lucide-react";
import type { LiveRoomMode } from "../types";

interface RoomModeBadgeProps {
  mode: LiveRoomMode;
}

const MODE_STYLES: Record<LiveRoomMode, string> = {
  PUBLIC:
    "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300",
};

const MODE_ICONS: Record<LiveRoomMode, React.ComponentType<{ className?: string }>> = {
  PUBLIC: Globe,
};

export function RoomModeBadge({ mode }: RoomModeBadgeProps) {
  const t = useTranslations("liveroom.mode");
  const Icon = MODE_ICONS[mode];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${MODE_STYLES[mode]}`}
    >
      <Icon className="size-3" />
      {t(mode)}
    </span>
  );
}
