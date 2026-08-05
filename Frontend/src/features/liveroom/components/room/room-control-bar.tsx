"use client";

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
import { useLiveroomStore } from "../../stores/use-liveroom-store";

interface RoomControlBarProps {
  cameraOn: boolean;
  micOn: boolean;
  micBlocked: boolean;
  busy: boolean;
  onToggleCamera: () => void;
  onToggleMic: () => void;
  onOpenParticipants: () => void;
  onOpenChat: () => void;
  onLeave: () => void;
  onEnd: () => void;
}

export function RoomControlBar({
  cameraOn,
  micOn,
  micBlocked,
  busy,
  onToggleCamera,
  onToggleMic,
  onOpenParticipants,
  onOpenChat,
  onLeave,
  onEnd,
}: RoomControlBarProps) {
  const t = useTranslations("liveroom.room.controls");
  const tHeader = useTranslations("liveroom.room.header");
  const isOwner = useLiveroomStore((state) => state.isOwner);

  return (
    <div className="flex h-20 shrink-0 items-center justify-center gap-3 border-t border-neutral-200 px-3 md:h-16 dark:border-neutral-800">
      <Button
        variant={micOn ? "default" : "outline"}
        size="icon"
        className="size-11 md:size-9"
        aria-label={micOn ? t("micOn") : t("micOff")}
        disabled={busy || micBlocked}
        onClick={onToggleMic}
      >
        {micOn ? <Mic className="size-5 md:size-4" /> : <MicOff className="size-5 md:size-4" />}
      </Button>

      <Button
        variant={cameraOn ? "default" : "outline"}
        size="icon"
        className="size-11 md:size-9"
        aria-label={cameraOn ? t("cameraOn") : t("cameraOff")}
        disabled={busy}
        onClick={onToggleCamera}
      >
        {cameraOn ? (
          <Camera className="size-5 md:size-4" />
        ) : (
          <CameraOff className="size-5 md:size-4" />
        )}
      </Button>

      <Button
        variant="outline"
        size="icon"
        className="size-11 md:size-9 lg:hidden"
        aria-label={t("participants")}
        onClick={onOpenParticipants}
      >
        <Users className="size-5 md:size-4" />
      </Button>

      <Button
        variant="outline"
        size="icon"
        className="size-11 md:size-9 lg:hidden"
        aria-label={t("chat")}
        onClick={onOpenChat}
      >
        <MessageSquare className="size-5 md:size-4" />
      </Button>

      {isOwner ? (
        <Button
          variant="destructive"
          size="icon"
          className="size-11 md:size-9"
          aria-label={tHeader("end")}
          onClick={onEnd}
        >
          <Square className="size-5 md:size-4" />
        </Button>
      ) : null}

      <Button
        variant="destructive"
        size="icon"
        className="size-11 md:size-9"
        aria-label={t("leave")}
        onClick={onLeave}
      >
        <LogOut className="size-5 md:size-4" />
      </Button>
    </div>
  );
}