"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import type { LiveRoom, LiveRoomMode, LiveRoomStatus } from "../types";
import { RoomStatusBadge } from "./room-status-badge";
import { RoomModeBadge } from "./room-mode-badge";

export type LiveRoomHeaderVariant = "immersive" | "card";

interface LiveRoomHeaderProps {
  title: string;
  roomCode: string;
  status?: LiveRoomStatus;
  mode?: LiveRoomMode;
  badges?: ReactNode;
  onClose?: () => void;
  variant?: LiveRoomHeaderVariant;
}

export function LiveRoomHeader({
  title,
  roomCode,
  status,
  mode,
  badges,
  onClose,
  variant = "immersive",
}: LiveRoomHeaderProps) {
  const tCommon = useTranslations("liveroom.actions");
  const tClose = useTranslations("liveroom.immersive");

  const wrapperClass =
    variant === "immersive"
      ? "absolute inset-x-0 top-0 z-30 flex items-center justify-between gap-3 bg-gradient-to-b from-black/70 via-black/40 to-transparent px-6 py-4"
      : "absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3";

  const pillClass =
    variant === "immersive"
      ? "flex min-w-0 items-center gap-2 rounded-full bg-black/50 px-3 py-1.5 backdrop-blur-md"
      : "flex flex-wrap items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm";

  const titleClass =
    variant === "immersive"
      ? "max-w-[40ch] truncate text-sm font-semibold text-white"
      : "text-sm font-medium text-white";

  const closeClass =
    variant === "immersive"
      ? "inline-flex size-10 items-center justify-center rounded-full bg-black/50 text-white backdrop-blur-md transition hover:bg-black/70"
      : "inline-flex size-8 items-center justify-center rounded-full bg-black/60 px-3 text-xs text-white backdrop-blur-sm transition hover:bg-black/80";

  return (
    <div className={wrapperClass}>
      <div className={pillClass}>
        <h1 className={titleClass}>{title}</h1>
        <span className="font-mono text-xs text-neutral-300">{roomCode}</span>
        {status ? <RoomStatusBadge status={status} /> : null}
        {mode ? <RoomModeBadge mode={mode} /> : null}
        {badges}
      </div>
      {onClose ? (
        <button
          type="button"
          onClick={onClose}
          aria-label={tClose("close")}
          title={tCommon("back")}
          className={closeClass}
        >
          {variant === "immersive" ? (
            <X className="size-4" />
          ) : (
            <span aria-hidden>✕</span>
          )}
        </button>
      ) : null}
    </div>
  );
}

export function headerBadgesForRoom(room: LiveRoom): ReactNode {
  return (
    <>
      <RoomStatusBadge status={room.status} />
      <RoomModeBadge mode={room.mode} />
    </>
  );
}
