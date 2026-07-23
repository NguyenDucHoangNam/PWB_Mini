"use client";

import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { ParticipantsList } from "./participants-list";

interface ImmersiveRightPanelProps {
  open: boolean;
  roomCode: string;
  hostUserId: string;
  onClose: () => void;
}

export function ImmersiveRightPanel({
  open,
  roomCode,
  hostUserId,
  onClose,
}: ImmersiveRightPanelProps) {
  const tImmersive = useTranslations("liveroom.immersive");

  return (
    <aside
      aria-hidden={!open}
      className={`absolute inset-y-0 right-0 z-20 flex w-full max-w-sm flex-col border-l border-white/5 bg-neutral-900 shadow-2xl transition-transform duration-300 ease-out ${
        open ? "translate-x-0" : "translate-x-full"
      }`}
    >
      <div className="flex items-center justify-between border-b border-white/5 px-4 py-3">
        <div className="flex items-center gap-2 text-sm font-medium text-white">
          <span>{tImmersive("people")}</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={tImmersive("close")}
          className="inline-flex size-8 items-center justify-center rounded-full text-neutral-400 transition hover:bg-white/5 hover:text-white"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4">
        <ParticipantsList roomCode={roomCode} hostUserId={hostUserId} />
      </div>
    </aside>
  );
}
