"use client";

import { useTranslations } from "next-intl";
import { Camera, CameraOff, Mic, MicOff, ShieldOff, UserMinus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AvatarInitials } from "../ui/avatar-initials";
import { displayName } from "../../utils/participant-sort";
import type { Participant } from "../../types";

interface ParticipantRowProps {
  participant: Participant;
  isMe: boolean;
  canModerate: boolean;
  onKick: (participant: Participant) => void;
  onMute: (participant: Participant) => void;
  muting: boolean;
}

export function ParticipantRow({
  participant,
  isMe,
  canModerate,
  onKick,
  onMute,
  muting,
}: ParticipantRowProps) {
  const t = useTranslations("liveroom.room.participants");

  const mutedByOwner = participant.micState === "MUTED_BY_OWNER";

  return (
    <li className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-neutral-100 dark:hover:bg-neutral-900">
      <AvatarInitials
        email={participant.userEmail}
        seed={participant.userId}
        className="size-9 text-xs"
      />

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-black dark:text-white">
          {displayName(participant)}
          {isMe ? (
            <span className="ml-1.5 text-xs text-neutral-500 dark:text-neutral-400">
              ({t("you")})
            </span>
          ) : null}
        </p>
        <p className="truncate text-xs text-neutral-500 dark:text-neutral-400">
          {participant.roomRole === "OWNER" ? t("owner") : t("guest")}
          {mutedByOwner ? ` · ${t("mutedByOwner")}` : ""}
        </p>
      </div>

      <span
        className={mutedByOwner ? "text-red-600 dark:text-red-400" : "text-neutral-400"}
        title={
          mutedByOwner ? t("mutedByOwner") : participant.micOn ? undefined : t("selfMuted")
        }
      >
        {participant.micOn ? (
          <Mic className="size-4" aria-label={t("selfMuted")} />
        ) : mutedByOwner ? (
          <ShieldOff className="size-4" aria-label={t("mutedByOwner")} />
        ) : (
          <MicOff className="size-4" aria-label={t("selfMuted")} />
        )}
      </span>
      <span className="text-neutral-400" title={participant.cameraOn ? undefined : t("cameraOff")}>
        {participant.cameraOn ? (
          <Camera className="size-4" aria-hidden />
        ) : (
          <CameraOff className="size-4" aria-label={t("cameraOff")} />
        )}
      </span>

      {canModerate && !isMe ? (
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-11 md:size-8"
            aria-label={t("mute")}
            disabled={muting || mutedByOwner}
            onClick={() => onMute(participant)}
          >
            <ShieldOff className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="size-11 text-red-600 md:size-8 dark:text-red-400"
            aria-label={t("kick")}
            onClick={() => onKick(participant)}
          >
            <UserMinus className="size-4" />
          </Button>
        </div>
      ) : null}
    </li>
  );
}