"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Crown, Loader2, MicOff, ShieldOff, WifiOff } from "lucide-react";
import { UserAvatar } from "../ui/user-avatar";
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
  const isRoomOwner = participant.roomRole === "OWNER";

  return (
    <div
      className={`neu-pressed relative overflow-hidden rounded-2xl border-none ${
        featured ? "col-span-2 row-span-2" : ""
      } ${
        isRoomOwner
          ? "outline-2 -outline-offset-2 outline-amber-700 dark:outline-amber-400"
          : speaking
            ? "outline-2 -outline-offset-2 outline-emerald-700 dark:outline-emerald-400"
            : ""
      }`}
    >
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className={`size-full -scale-x-100 object-cover ${showVideo ? "" : "hidden"}`}
      />

      {!isMe ? <audio ref={audioRef} autoPlay playsInline /> : null}

      {!showVideo ? (
        <div className="flex size-full items-center justify-center">
          <UserAvatar
            email={participant.userEmail}
            avatarUrl={participant.avatarUrl}
            seed={participant.userId}
            className={`${
              featured
                ? "size-24 text-3xl md:size-28 md:text-4xl"
                : "size-16 text-xl md:size-20 md:text-2xl"
            } ${isRoomOwner ? "outline-2 outline-offset-2 outline-amber-700 dark:outline-amber-400" : ""}`}
          />
        </div>
      ) : null}

      {isRoomOwner ? (
        <>
          <span
            className="neu-raised-sm absolute top-2 left-2 flex items-center gap-1 rounded-full border-none px-2 py-1 text-[10px] leading-none font-bold text-amber-800 dark:text-amber-400"
            title={tParticipants("owner")}
          >
            <Crown className="size-3" aria-hidden />
            <span className="sr-only sm:not-sr-only">{tParticipants("owner")}</span>
          </span>
        </>
      ) : null}

      {connecting ? (
        <div className="absolute inset-0 flex items-center justify-center gap-2 bg-slate-900/45 text-xs font-semibold text-white">
          <Loader2 className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />
          {t("connecting")}
        </div>
      ) : failed ? (
        <div className="absolute inset-0 flex items-center justify-center gap-2 bg-slate-900/55 text-xs font-semibold text-white">
          <WifiOff className="size-4" aria-hidden />
          {t("connectionLost")}
        </div>
      ) : null}

      <div className="absolute inset-x-0 bottom-0 flex items-center gap-1.5 bg-gradient-to-t from-slate-900/75 to-transparent px-2.5 py-2">
        {mutedByOwner ? (
          <ShieldOff
            className="size-3.5 shrink-0 text-rose-300"
            aria-label={tParticipants("mutedByOwner")}
          />
        ) : !participant.micOn ? (
          <MicOff
            className="size-3.5 shrink-0 text-slate-200"
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
                  audioLevel >= bar ? "bg-emerald-300" : "bg-white/35"
                }`}
                style={{ height: `${3 + bar * 2}px` }}
              />
            ))}
          </span>
        )}
        <span className="truncate text-xs font-semibold text-white">
          {isMe ? t("you") : displayName(participant)}
        </span>
      </div>
    </div>
  );
}