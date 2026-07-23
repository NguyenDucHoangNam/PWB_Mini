"use client";

import { MonitorUp } from "lucide-react";
import { useTranslations } from "next-intl";

interface ScreenShareControlProps {
  isSharing: boolean;
  onToggle: () => void;
}

export function ScreenShareControl({ isSharing, onToggle }: ScreenShareControlProps) {
  const tImmersive = useTranslations("liveroom.immersive");

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={isSharing ? tImmersive("screenShareStop") : tImmersive("screenShareStart")}
      aria-pressed={isSharing}
      title={isSharing ? tImmersive("screenShareStop") : tImmersive("screenShareStart")}
      className={`inline-flex size-12 items-center justify-center rounded-full transition ${
        isSharing
          ? "bg-blue-500 text-white"
          : "bg-neutral-700 text-white hover:bg-neutral-600"
      }`}
    >
      <MonitorUp className="size-5" />
    </button>
  );
}
