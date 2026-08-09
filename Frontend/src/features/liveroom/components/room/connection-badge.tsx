"use client";

import { useTranslations } from "next-intl";
import { Loader2, Wifi, WifiOff } from "lucide-react";
import { NeuButton } from "@/components/ui/neu";
import { liveroomSocket, type ConnectionStatus } from "../../lib/liveroom-socket";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

const TONE: Record<ConnectionStatus, string> = {
  idle: "text-slate-600 dark:text-slate-400",
  connecting: "text-slate-600 dark:text-slate-400",
  connected: "text-emerald-800 dark:text-emerald-400",
  reconnecting: "text-amber-800 dark:text-amber-400",
  offline: "text-rose-700 dark:text-rose-400",
  unauthorized: "text-rose-700 dark:text-rose-400",
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
    <div className={`flex items-center gap-1.5 text-xs font-semibold ${TONE[status]}`}>
      {busy ? (
        <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden />
      ) : broken ? (
        <WifiOff className="size-3.5" aria-hidden />
      ) : (
        <Wifi className="size-3.5" aria-hidden />
      )}
      <span aria-live="polite">{t(LABEL_KEY[status])}</span>
      {broken ? (
        <NeuButton
          variant="ghost"
          size="sm"
          className="h-8 px-3 text-current"
          onClick={() => liveroomSocket.retry()}
        >
          {t("retry")}
        </NeuButton>
      ) : null}
    </div>
  );
}