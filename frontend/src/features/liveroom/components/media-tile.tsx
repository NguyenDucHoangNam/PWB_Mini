"use client";

import { useEffect, useRef, useState } from "react";
import { Mic, MicOff, Video, VideoOff } from "lucide-react";
import { useTranslations } from "next-intl";

interface MediaTileProps {
  stream: MediaStream | null;
  cameraOff: boolean;
  micMuted: boolean;
  displayName: string;
  isLocal: boolean;
  isSpeaking?: boolean;
  hint?: string;
}

export function MediaTile({
  stream,
  cameraOff,
  micMuted,
  displayName,
  isLocal,
  isSpeaking = false,
  hint,
}: MediaTileProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [videoFrameAvailable, setVideoFrameAvailable] = useState(false);
  const tCommon = useTranslations("common");
  const tMedia = useTranslations("liveroom.media");

  useEffect(() => {
    const node = videoRef.current;
    if (!node) return;

    if (!stream) {
      node.srcObject = null;
      setVideoFrameAvailable(false);
      return;
    }

    node.srcObject = stream;

    const evaluateAvailability = (): boolean => {
      const videoTrack = stream.getVideoTracks()[0];
      if (!videoTrack) return false;
      const settings = (videoTrack.getSettings && videoTrack.getSettings()) || {};
      const isLive = videoTrack.readyState === "live" && !videoTrack.muted;
      const hasDimensions = (settings.width ?? 0) > 0 && (settings.height ?? 0) > 0;
      return isLive && hasDimensions;
    };

    const syncAvailability = () => {
      const available = evaluateAvailability();
      setVideoFrameAvailable(available);
      if (available && node.paused) {
        node.play().catch(() => {});
      }
    };

    syncAvailability();

    const handleVideoFrame = () => syncAvailability();

    if (typeof node.requestVideoFrameCallback === "function") {
      node.requestVideoFrameCallback(handleVideoFrame);
    }

    const intervalId = window.setInterval(handleVideoFrame, 500);

    let lastVideoTrackId = stream.getVideoTracks()[0]?.id ?? null;
    const trackRevisionInterval = window.setInterval(() => {
      const currentTrackId = stream.getVideoTracks()[0]?.id ?? null;
      if (currentTrackId !== lastVideoTrackId) {
        lastVideoTrackId = currentTrackId;
        node.srcObject = null;
        node.srcObject = stream;
        syncAvailability();
      }
    }, 250);

    const tracks = stream.getVideoTracks();
    const trackListeners: Array<{ track: MediaStreamTrack; type: string; handler: () => void }> = [];
    for (const track of tracks) {
      const handler = () => syncAvailability();
      track.addEventListener("mute", handler);
      track.addEventListener("unmute", handler);
      track.addEventListener("ended", handler);
      track.addEventListener("settingschange", handler);
      track.addEventListener("started", handler);
      trackListeners.push(
        { track, type: "mute", handler },
        { track, type: "unmute", handler },
        { track, type: "ended", handler },
        { track, type: "settingschange", handler },
        { track, type: "started", handler },
      );
    }

    return () => {
      window.clearInterval(intervalId);
      window.clearInterval(trackRevisionInterval);
      for (const { track, type, handler } of trackListeners) {
        track.removeEventListener(type, handler);
      }
    };
  }, [stream]);

  useEffect(() => {
    const node = videoRef.current;
    if (!node) return;
    if (cameraOff) {
      node.srcObject = null;
      setVideoFrameAvailable(false);
    } else if (stream) {
      node.srcObject = stream;
    }
  }, [cameraOff, stream]);

  const showVideo = !cameraOff && !!stream && videoFrameAvailable;
  const showSpeakingRing = isSpeaking && !micMuted;

  return (
    <div
      className={`relative aspect-video w-full overflow-hidden rounded-xl border-2 ${
        showSpeakingRing
          ? "border-green-400 ring-4 ring-green-400/60 shadow-[0_0_16px_4px_rgba(74,222,128,0.45)]"
          : "border-neutral-200 dark:border-neutral-800"
      } bg-black transition-all duration-200`}
    >
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={isLocal || micMuted}
        className={`h-full w-full object-cover [transform:scaleX(-1)] transition-opacity duration-150 ${
          showVideo ? "opacity-100" : "opacity-0"
        }`}
      />
      {!showVideo ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-neutral-900 text-neutral-200">
          <div className="flex size-14 items-center justify-center rounded-full bg-neutral-700 text-base font-semibold uppercase">
            {(displayName || "?").charAt(0)}
          </div>
          <span className="text-xs text-neutral-400">
            {stream ? tMedia("remoteCameraOff") : tCommon("loading")}
          </span>
        </div>
      ) : null}
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
    </div>
  );
}
