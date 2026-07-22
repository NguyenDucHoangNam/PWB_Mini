"use client";

import { useEffect, useRef } from "react";
import { Mic, MicOff, Video, VideoOff } from "lucide-react";
import { useTranslations } from "next-intl";

interface MediaTileProps {
  stream: MediaStream | null;
  cameraOff: boolean;
  micMuted: boolean;
  displayName: string;
  isLocal: boolean;
  hint?: string;
}

export function MediaTile({
  stream,
  cameraOff,
  micMuted,
  displayName,
  isLocal,
  hint,
}: MediaTileProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const tCommon = useTranslations("common");
  const tMedia = useTranslations("liveroom.media");

  useEffect(() => {
    const node = videoRef.current;
    if (!node) return;
    node.srcObject = stream;
    if (!stream) {
      node.load();
    }
  }, [stream]);

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl border border-neutral-200 bg-black dark:border-neutral-800">
      {!cameraOff && stream ? (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted={isLocal}
          className="h-full w-full object-cover"
        />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-2 bg-neutral-900 text-neutral-200">
          <div className="flex size-14 items-center justify-center rounded-full bg-neutral-700 text-base font-semibold uppercase">
            {(displayName || "?").charAt(0)}
          </div>
          <span className="text-xs text-neutral-400">{tMedia("remoteCameraOff")}</span>
        </div>
      )}
      <div className="absolute inset-x-3 bottom-3 flex items-end justify-between gap-2">
        <div className="flex items-center gap-2 rounded-full bg-black/55 px-3 py-1 text-xs text-white">
          <span className="max-w-[12ch] truncate">{displayName}</span>
          {isLocal ? (
            <span className="rounded-full bg-white/15 px-2 py-0.5 text-[10px] uppercase">
              {tMedia("youBadge")}
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-1 rounded-full bg-black/55 px-2 py-1 text-xs text-white">
          {micMuted ? (
            <span className="flex items-center gap-1 text-red-400" aria-label={tMedia("micOff")}>
              <MicOff className="size-4" />
              <span className="sr-only">{tMedia("micOff")}</span>
            </span>
          ) : (
            <span className="flex items-center gap-1 text-emerald-400" aria-label={tMedia("micOn")}>
              <Mic className="size-4" />
              <span className="sr-only">{tMedia("micOn")}</span>
            </span>
          )}
          {cameraOff ? (
            <span className="flex items-center gap-1 text-red-400" aria-label={tMedia("cameraOff")}>
              <VideoOff className="size-4" />
              <span className="sr-only">{tMedia("cameraOff")}</span>
            </span>
          ) : (
            <span className="flex items-center gap-1 text-emerald-400" aria-label={tMedia("cameraOn")}>
              <Video className="size-4" />
              <span className="sr-only">{tMedia("cameraOn")}</span>
            </span>
          )}
        </div>
      </div>
      {hint ? (
        <div className="absolute inset-x-3 top-3 rounded-full bg-black/55 px-3 py-1 text-xs text-white">
          {hint}
        </div>
      ) : null}
      {!stream && !cameraOff ? (
        <div className="absolute inset-0 flex items-center justify-center bg-black/70 text-xs text-neutral-300">
          {tCommon("loading")}
        </div>
      ) : null}
    </div>
  );
}
