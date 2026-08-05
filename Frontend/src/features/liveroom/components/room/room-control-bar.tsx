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
import { Button } from "@/components/ui/button";
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

function ControlButton({
  label,
  children,
  onClick,
  variant = "outline",
  disabled = false,
  pressed,
  alertCount = 0,
}: {
  label: string;
  children: ReactNode;
  onClick: () => void;
  variant?: "default" | "outline" | "destructive";
  disabled?: boolean;
  pressed?: boolean;
  alertCount?: number;
}) {
  return (
    <Tooltip>
      <span className="relative inline-flex">
        <TooltipTrigger
          render={
            <Button
              variant={variant}
              size="icon"
              className="size-11 md:size-10"
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
          <span
            aria-hidden
            className="pointer-events-none absolute -top-1 -right-1 z-10 inline-flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums ring-2 ring-white dark:ring-black"
          >
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
    <div className="flex h-20 shrink-0 items-center justify-center gap-2 border-t border-neutral-200 px-3 md:h-16 md:gap-3 dark:border-neutral-800">
      <ControlButton
        label={micOn ? t("micOn") : t("micOff")}
        variant={micOn ? "default" : "outline"}
        disabled={busy || micBlocked}
        pressed={micOn}
        onClick={onToggleMic}
      >
        {micOn ? <Mic className="size-5 md:size-4" /> : <MicOff className="size-5 md:size-4" />}
      </ControlButton>

      <ControlButton
        label={cameraOn ? t("cameraOn") : t("cameraOff")}
        variant={cameraOn ? "default" : "outline"}
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

      <span className="mx-1 h-8 w-px bg-neutral-200 dark:bg-neutral-800" aria-hidden />

      <Tooltip>
        <span className="relative inline-flex">
          <TooltipTrigger
            render={
              <Button
                variant={participantsOpen ? "default" : "outline"}
                className="h-11 gap-1.5 px-3 md:h-10"
                aria-pressed={participantsOpen}
                onClick={onToggleParticipants}
              />
            }
          >
            <Users className="size-5 md:size-4" />
            <span className="text-sm font-semibold tabular-nums">{participantCount}</span>
          </TooltipTrigger>
          {waiting > 0 ? (
            <span
              aria-hidden
              className="pointer-events-none absolute -top-1 -right-1 z-10 inline-flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums ring-2 ring-white dark:ring-black"
            >
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

      <ControlButton
        label={t("chat")}
        variant={chatOpen ? "default" : "outline"}
        pressed={chatOpen}
        onClick={onToggleChat}
      >
        <MessageSquare className="size-5 md:size-4" />
      </ControlButton>

      <span className="mx-1 h-8 w-px bg-neutral-200 dark:bg-neutral-800" aria-hidden />

      {isOwner ? (
        <ControlButton label={tHeader("end")} variant="destructive" onClick={onEnd}>
          <Square className="size-5 md:size-4" />
        </ControlButton>
      ) : null}

      <ControlButton label={t("leave")} variant="destructive" onClick={onLeave}>
        <LogOut className="size-5 md:size-4" />
      </ControlButton>
    </div>
  );
}