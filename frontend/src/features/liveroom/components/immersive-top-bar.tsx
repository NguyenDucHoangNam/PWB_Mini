"use client";

import { useTranslations } from "next-intl";
import { RoomStatusBadge } from "./room-status-badge";
import type { LiveRoom } from "../types";

interface ImmersiveTopBarProps {
  room: LiveRoom;
  onClose: () => void;
}

export function ImmersiveTopBar({ room, onClose }: ImmersiveTopBarProps) {
  const tImmersive = useTranslations("liveroom.immersive");

  return (
    <div className="absolute inset-x-0 top-0 z-30 flex items-center justify-between gap-3 bg-gradient-to-b from-black/70 via-black/40 to-transparent px-6 py-4">
      <div className="flex min-w-0 items-center gap-3">
        <div className="flex min-w-0 items-center gap-2 rounded-full bg-black/50 px-3 py-1.5 backdrop-blur-md">
          <h1 className="max-w-[40ch] truncate text-sm font-semibold text-white">
            {room.title}
          </h1>
          <span className="font-mono text-xs text-neutral-300">
            {room.roomCode}
          </span>
          <RoomStatusBadge status={room.status} />
        </div>
      </div>
      <button
        type="button"
        onClick={onClose}
        aria-label={tImmersive("close")}
        title={tImmersive("close")}
        className="inline-flex size-10 items-center justify-center rounded-full bg-black/50 text-white backdrop-blur-md transition hover:bg-black/70"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M18 6L6 18M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}