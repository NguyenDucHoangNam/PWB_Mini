"use client";

import { useTranslations } from "next-intl";
import { Camera, CameraOff, Mic, MicOff, ShieldOff, UserMinus } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { UserAvatar } from "../ui/user-avatar";
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
    <li className="neu-raised-sm flex items-center gap-3 rounded-2xl border-none px-3 py-2.5">
      <UserAvatar
        email={participant.userEmail}
        avatarUrl={participant.avatarUrl}
        seed={participant.userId}
        className="size-9 text-xs"
      />

      <div className="min-w-0 flex-1">
        <p className={`truncate text-sm font-bold ${NEU_TEXT}`}>
          {displayName(participant)}
          {isMe ? (
            <span className={`ml-1.5 text-xs font-medium ${NEU_TEXT_MUTED}`}>
              ({t("you")})
            </span>
          ) : null}
        </p>
        <p className={`truncate text-xs font-medium ${NEU_TEXT_MUTED}`}>
          {participant.roomRole === "OWNER" ? t("owner") : t("guest")}
          {mutedByOwner ? ` · ${t("mutedByOwner")}` : ""}
        </p>
      </div>

      <span
        className={mutedByOwner ? "text-rose-700 dark:text-rose-400" : "text-slate-500 dark:text-slate-400"}
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
      <span className="text-slate-500 dark:text-slate-400" title={participant.cameraOn ? undefined : t("cameraOff")}>
        {participant.cameraOn ? (
          <Camera className="size-4" aria-hidden />
        ) : (
          <CameraOff className="size-4" aria-label={t("cameraOff")} />
        )}
      </span>

      {canModerate && !isMe ? (
        <div className="flex items-center gap-1">
          <Tooltip>
            <TooltipTrigger
              render={
                <NeuButton
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("mute")}
                  disabled={muting || mutedByOwner}
                  onClick={() => onMute(participant)}
                />
              }
            >
              <ShieldOff className="size-4" />
            </TooltipTrigger>
            <TooltipContent>{t("mute")}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <NeuButton
                  variant="ghost"
                  size="icon-sm"
                  className="text-rose-700 hover:text-rose-800 dark:text-rose-400 dark:hover:text-rose-300"
                  aria-label={t("kick")}
                  onClick={() => onKick(participant)}
                />
              }
            >
              <UserMinus className="size-4" />
            </TooltipTrigger>
            <TooltipContent>{t("kick")}</TooltipContent>
          </Tooltip>
        </div>
      ) : null}
    </li>
  );
}