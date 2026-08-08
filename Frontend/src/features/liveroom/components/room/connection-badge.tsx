"use client";

import { useTranslations } from "next-intl";
import { Loader2, Wifi, WifiOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { liveroomSocket, type ConnectionStatus } from "../../lib/liveroom-socket";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

const TONE: Record<ConnectionStatus, string> = {
  idle: "text-neutral-500 dark:text-neutral-400",
  connecting: "text-neutral-500 dark:text-neutral-400",
  connected: "text-green-700 dark:text-green-400",
  reconnecting: "text-amber-700 dark:text-amber-400",
  offline: "text-red-600 dark:text-red-400",
  unauthorized: "text-red-600 dark:text-red-400",
};

const LABEL_KEY: Record<ConnectionStatus, string> = {
  idle: "connecting",
  connecting: "connecting",
  connected: "connected",
  reconnecting: "reconnecting",
  offline: "offline",
  unauthorized: "unauthorized",
};

export function ConnectionBadge() {
  const t = useTranslations("liveroom.room.connection");
  const status = useLiveroomStore((state) => state.connection.status);

  const busy = status === "connecting" || status === "reconnecting";
  const broken = status === "offline" || status === "unauthorized";

  return (
    <div className={`flex items-center gap-1.5 text-xs font-medium ${TONE[status]}`}>
      {busy ? (
        <Loader2 className="size-3.5 animate-spin" aria-hidden />
      ) : broken ? (
        <WifiOff className="size-3.5" aria-hidden />
      ) : (
        <Wifi className="size-3.5" aria-hidden />
      )}
      <span aria-live="polite">{t(LABEL_KEY[status])}</span>
      {broken ? (
        <Button
          variant="ghost"
          size="xs"
          className="h-7 px-2"
          onClick={() => liveroomSocket.retry()}
        >
          {t("retry")}
        </Button>
      ) : null}
    </div>
  );
}