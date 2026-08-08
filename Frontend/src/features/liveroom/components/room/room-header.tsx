"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Check, Copy, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ConnectionBadge } from "./connection-badge";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { formatRoomCode } from "../../utils/format-room-code";

export function RoomHeader() {
  const t = useTranslations("liveroom.room.header");
  const tList = useTranslations("liveroom.list");
  const tControls = useTranslations("liveroom.room.controls");
  const room = useLiveroomStore((state) => state.room);
  const participantCount = useLiveroomStore(
    (state) => Object.keys(state.participants).length,
  );
  const [copied, setCopied] = useState(false);

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(room.roomCode);
      setCopied(true);
      toast.success(tList("copied"));
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(tList("copyCode"));
    }
  };

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-neutral-200 px-3 md:gap-4 md:px-4 dark:border-neutral-800">
      <h1 className="min-w-0 flex-1 truncate text-sm font-semibold text-black md:text-base dark:text-white">
        {room.roomName}
      </h1>

      <div className="hidden items-center gap-1 sm:flex">
        <span className="rounded-md border border-neutral-200 bg-neutral-50 px-2 py-1 font-mono text-xs tracking-[0.2em] text-black dark:border-neutral-800 dark:bg-neutral-900 dark:text-white">
          {formatRoomCode(room.roomCode)}
        </span>
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                variant="ghost"
                size="icon"
                className="size-11 md:size-8"
                aria-label={tList("copyCode")}
                onClick={copyCode}
              />
            }
          >
            {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
          </TooltipTrigger>
          <TooltipContent>{copied ? tList("copied") : tList("copyCode")}</TooltipContent>
        </Tooltip>
      </div>

      <span
        title={tControls("participants")}
        className="flex items-center gap-1 rounded-full bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700 tabular-nums dark:bg-neutral-900 dark:text-neutral-200"
      >
        <Users className="size-3.5" aria-hidden />
        {t("participantCount", {
          count: participantCount,
          max: room.effectiveMaxParticipants || room.maxParticipants,
        })}
      </span>

      <ConnectionBadge />
    </header>
  );
}