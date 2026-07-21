"use client";

import { useTranslations } from "next-intl";
import { Lock, Globe, Users, KeyRound } from "lucide-react";
import type { LiveRoomMode } from "../types";

interface RoomModeBadgeProps {
  mode: LiveRoomMode;
}

const MODE_STYLES: Record<LiveRoomMode, string> = {
  PUBLIC:
    "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300",
  PRIVATE:
    "border-purple-300 bg-purple-50 text-purple-800 dark:border-purple-800 dark:bg-purple-950/30 dark:text-purple-300",
  INVITE_ONLY:
    "border-orange-300 bg-orange-50 text-orange-800 dark:border-orange-800 dark:bg-orange-950/30 dark:text-orange-300",
  PASSWORD:
    "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300",
};

const MODE_ICONS: Record<LiveRoomMode, React.ComponentType<{ className?: string }>> = {
  PUBLIC: Globe,
  PRIVATE: Lock,
  INVITE_ONLY: Users,
  PASSWORD: KeyRound,
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
