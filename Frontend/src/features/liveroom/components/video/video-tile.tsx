"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Loader2, MicOff, ShieldOff, WifiOff } from "lucide-react";
import { AvatarInitials } from "../ui/avatar-initials";
import { useAudioLevel } from "../../hooks/use-audio-level";
import { displayName } from "../../utils/participant-sort";
import type { Participant } from "../../types";

interface VideoTileProps {
  participant: Participant;
  stream: MediaStream | null;
  videoActive: boolean;
  localAudioLevel?: number;
  connectionState?: RTCPeerConnectionState;
  isMe?: boolean;
  featured?: boolean;
}

export function VideoTile({
  participant,
  stream,
  videoActive,
  localAudioLevel = 0,
  connectionState,
  isMe = false,
  featured = false,
}: VideoTileProps) {
  const t = useTranslations("liveroom.room.video");
  const tParticipants = useTranslations("liveroom.room.participants");
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    const element = videoRef.current;
    if (!element) return;
    if (element.srcObject !== stream) element.srcObject = stream;
    if (stream) void element.play().catch(() => undefined);
  }, [stream]);

  useEffect(() => {
    const element = audioRef.current;
    if (!element || isMe) return;
    if (element.srcObject !== stream) element.srcObject = stream;
    if (!stream) return;

    let cancelled = false;
    const attempt = () => {
      if (!cancelled) void element.play().catch(() => undefined);
    };
    attempt();
    document.addEventListener("click", attempt);
    return () => {
      cancelled = true;
      document.removeEventListener("click", attempt);
    };
  }, [stream, isMe]);

  const remoteAudioLevel = useAudioLevel(isMe ? null : stream);
  const audioLevel = isMe ? localAudioLevel : remoteAudioLevel;
  const speaking = Boolean(stream) && audioLevel > 0;
  const showVideo = Boolean(stream) && videoActive;
  const connecting = !isMe && (connectionState === "connecting" || connectionState === "new");
  const failed = !isMe && (connectionState === "failed" || connectionState === "disconnected");
  const mutedByOwner = participant.micState === "MUTED_BY_OWNER";

  return (
    <div
      className={`relative overflow-hidden rounded-xl bg-neutral-900 ${
        featured ? "col-span-2 row-span-2" : ""
      } ${speaking ? "ring-2 ring-emerald-400" : ""}`}
    >
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className={`size-full object-cover ${showVideo ? "" : "hidden"}`}
      />

      {!isMe ? <audio ref={audioRef} autoPlay playsInline /> : null}

      {!showVideo ? (
        <div className="flex size-full items-center justify-center">
          <AvatarInitials
            email={participant.userEmail}
            seed={participant.userId}
            className="size-14 text-lg md:size-16 md:text-xl"
          />
        </div>
      ) : null}

      {connecting ? (
        <div className="absolute inset-0 flex items-center justify-center gap-2 bg-black/40 text-xs text-white">
          <Loader2 className="size-4 animate-spin" aria-hidden />
          {t("connecting")}
        </div>
      ) : failed ? (
        <div className="absolute inset-0 flex items-center justify-center gap-2 bg-black/50 text-xs text-white">
          <WifiOff className="size-4" aria-hidden />
          {t("connectionLost")}
        </div>
      ) : null}

      <div className="absolute inset-x-0 bottom-0 flex items-center gap-1.5 bg-gradient-to-t from-black/70 to-transparent px-2 py-1.5">
        {mutedByOwner ? (
          <ShieldOff
            className="size-3.5 shrink-0 text-red-400"
            aria-label={tParticipants("mutedByOwner")}
          />
        ) : !participant.micOn ? (
          <MicOff
            className="size-3.5 shrink-0 text-neutral-300"
            aria-label={tParticipants("selfMuted")}
          />
        ) : (
          <span
            className="flex shrink-0 items-end gap-0.5"
            aria-label={t("micLevel")}
            title={t("micLevel")}
          >
            {[1, 2, 3, 4].map((bar) => (
              <span
                key={bar}
                className={`w-0.5 rounded-full ${
                  audioLevel >= bar ? "bg-emerald-400" : "bg-white/30"
                }`}
                style={{ height: `${3 + bar * 2}px` }}
              />
            ))}
          </span>
        )}
        <span className="truncate text-xs font-medium text-white">
          {isMe ? t("you") : displayName(participant)}
        </span>
      </div>
    </div>
  );
}