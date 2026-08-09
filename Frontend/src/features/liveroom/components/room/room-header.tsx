"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Check, Copy, Users } from "lucide-react";
import { NEU_TEXT, NeuBadge, NeuButton } from "@/components/ui/neu";
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
    <header className="neu-raised m-3 mb-0 flex h-14 shrink-0 items-center gap-2 rounded-2xl border-none px-3 md:gap-4 md:px-4">
      <h1 className={`min-w-0 flex-1 truncate text-sm font-bold md:text-base ${NEU_TEXT}`}>
        {room.roomName}
      </h1>

      <div className="hidden items-center gap-2 sm:flex">
        <span
          className={`neu-pressed-sm rounded-xl border-none px-2.5 py-1.5 font-mono text-xs font-bold tracking-[0.2em] ${NEU_TEXT}`}
        >
          {formatRoomCode(room.roomCode)}
        </span>
        <Tooltip>
          <TooltipTrigger
            render={
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className={copied ? "text-indigo-600 dark:text-indigo-400" : ""}
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

      <NeuBadge tone="muted" title={tControls("participants")} className="tabular-nums">
        <Users className="size-3.5" aria-hidden />
        {t("participantCount", {
          count: participantCount,
          max: room.effectiveMaxParticipants || room.maxParticipants,
        })}
      </NeuBadge>

      <ConnectionBadge />
    </header>
  );
}