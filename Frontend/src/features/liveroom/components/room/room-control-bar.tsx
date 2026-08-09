"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import {
  Camera,
  CameraOff,
  LogOut,
  MessageSquare,
  Mic,
  MicOff,
  Square,
  Users,
} from "lucide-react";
import { NeuButton } from "@/components/ui/neu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

interface RoomControlBarProps {
  cameraOn: boolean;
  micOn: boolean;
  micBlocked: boolean;
  busy: boolean;
  participantsOpen: boolean;
  chatOpen: boolean;
  onToggleCamera: () => void;
  onToggleMic: () => void;
  onToggleParticipants: () => void;
  onToggleChat: () => void;
  onLeave: () => void;
  onEnd: () => void;
}

const ALERT_BADGE =
  "pointer-events-none absolute -top-1 -right-1 z-10 inline-flex min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums dark:bg-rose-500";

const ACTIVE_TOGGLE = "neu-pressed text-indigo-600 dark:text-indigo-400";

function ControlButton({
  label,
  children,
  onClick,
  tone = "neutral",
  disabled = false,
  pressed,
  alertCount = 0,
}: {
  label: string;
  children: ReactNode;
  onClick: () => void;
  tone?: "neutral" | "danger";
  disabled?: boolean;
  pressed?: boolean;
  alertCount?: number;
}) {
  return (
    <Tooltip>
      <span className="relative inline-flex">
        <TooltipTrigger
          render={
            <NeuButton
              variant={tone === "danger" ? "danger" : "default"}
              size="icon"
              className={`md:size-10 ${pressed ? ACTIVE_TOGGLE : ""}`}
              aria-label={label}
              aria-pressed={pressed}
              disabled={disabled}
              onClick={onClick}
            />
          }
        >
          {children}
        </TooltipTrigger>
        {alertCount > 0 ? (
          <span aria-hidden className={ALERT_BADGE}>
            {alertCount > 99 ? "99+" : alertCount}
          </span>
        ) : null}
      </span>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

export function RoomControlBar({
  cameraOn,
  micOn,
  micBlocked,
  busy,
  participantsOpen,
  chatOpen,
  onToggleCamera,
  onToggleMic,
  onToggleParticipants,
  onToggleChat,
  onLeave,
  onEnd,
}: RoomControlBarProps) {
  const t = useTranslations("liveroom.room.controls");
  const tHeader = useTranslations("liveroom.room.header");
  const isOwner = useLiveroomStore((state) => state.isOwner);
  const participantCount = useLiveroomStore(
    (state) => Object.keys(state.participants).length,
  );
  const pendingCount = useLiveroomStore((state) => Object.keys(state.joinRequests).length);
  const waiting = isOwner ? pendingCount : 0;

  return (
    <div className="neu-raised m-3 mt-0 flex h-20 shrink-0 items-center justify-center gap-2 rounded-2xl border-none px-2 sm:gap-2.5 sm:px-3 md:h-16 md:gap-3">
      <ControlButton
        label={micOn ? t("micOn") : t("micOff")}
        disabled={busy || micBlocked}
        pressed={micOn}
        onClick={onToggleMic}
      >
        {micOn ? <Mic className="size-5 md:size-4" /> : <MicOff className="size-5 md:size-4" />}
      </ControlButton>

      <ControlButton
        label={cameraOn ? t("cameraOn") : t("cameraOff")}
        disabled={busy}
        pressed={cameraOn}
        onClick={onToggleCamera}
      >
        {cameraOn ? (
          <Camera className="size-5 md:size-4" />
        ) : (
          <CameraOff className="size-5 md:size-4" />
        )}
      </ControlButton>

      <span className="neu-pressed-sm mx-1 h-8 w-1 rounded-full border-none" aria-hidden />

      <Tooltip>
        <span className="relative inline-flex">
          <TooltipTrigger
            render={
              <NeuButton
                className={`h-11 gap-1.5 px-3 md:h-10 ${participantsOpen ? ACTIVE_TOGGLE : ""}`}
                aria-pressed={participantsOpen}
                onClick={onToggleParticipants}
              />
            }
          >
            <Users className="size-5 md:size-4" />
            <span className="text-sm font-bold tabular-nums">{participantCount}</span>
          </TooltipTrigger>
          {waiting > 0 ? (
            <span aria-hidden className={ALERT_BADGE}>
              {waiting > 99 ? "99+" : waiting}
            </span>
          ) : null}
        </span>
        <TooltipContent>
          {waiting > 0
            ? `${t("participants")} · ${t("waitingCount", { count: waiting })}`
            : t("participants")}
        </TooltipContent>
      </Tooltip>

      <ControlButton label={t("chat")} pressed={chatOpen} onClick={onToggleChat}>
        <MessageSquare className="size-5 md:size-4" />
      </ControlButton>

      <span className="neu-pressed-sm mx-1 h-8 w-1 rounded-full border-none" aria-hidden />

      {isOwner ? (
        <ControlButton label={tHeader("end")} tone="danger" onClick={onEnd}>
          <Square className="size-5 md:size-4" />
        </ControlButton>
      ) : null}

      <ControlButton label={t("leave")} tone="danger" onClick={onLeave}>
        <LogOut className="size-5 md:size-4" />
      </ControlButton>
    </div>
  );
}