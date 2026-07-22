"use client";

import { Mic, MicOff, PhoneOff, Video, VideoOff, Settings } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

interface MediaControlsProps {
  micMuted: boolean;
  cameraOff: boolean;
  selectingDevice: boolean;
  audioDevices: { deviceId: string; label: string }[];
  videoDevices: { deviceId: string; label: string }[];
  currentAudioId: string | null;
  currentVideoId: string | null;
  onToggleMic: () => void;
  onToggleCamera: () => void;
  onSelectAudio: (deviceId: string) => void;
  onSelectVideo: (deviceId: string) => void;
  onLeave: () => void;
}

export function MediaControls({
  micMuted,
  cameraOff,
  selectingDevice,
  audioDevices,
  videoDevices,
  currentAudioId,
  currentVideoId,
  onToggleMic,
  onToggleCamera,
  onSelectAudio,
  onSelectVideo,
  onLeave,
}: MediaControlsProps) {
  const tMedia = useTranslations("liveroom.media");

  return (
    <div className="flex items-center gap-2 rounded-full bg-neutral-800/90 px-4 py-2 shadow-2xl backdrop-blur-sm">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={onToggleMic}
        aria-label={micMuted ? tMedia("micOff") : tMedia("micOn")}
        title={micMuted ? tMedia("micOff") : tMedia("micOn")}
        className={`size-11 rounded-full ${micMuted ? "bg-red-500/90 text-white hover:bg-red-600" : "text-white hover:bg-neutral-700"}`}
      >
        {micMuted ? <MicOff className="size-5" /> : <Mic className="size-5" />}
      </Button>

      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={onToggleCamera}
        aria-label={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
        title={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
        className={`size-11 rounded-full ${cameraOff ? "bg-red-500/90 text-white hover:bg-red-600" : "text-white hover:bg-neutral-700"}`}
      >
        {cameraOff ? <VideoOff className="size-5" /> : <Video className="size-5" />}
      </Button>

      {audioDevices.length > 1 || videoDevices.length > 1 ? (
        <div className="relative group">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="Device settings"
            title="Device settings"
            className="size-11 rounded-full text-white hover:bg-neutral-700"
          >
            <Settings className="size-5" />
          </Button>
          <div className="absolute bottom-full left-1/2 z-20 mb-2 hidden w-56 -translate-x-1/2 rounded-xl border border-neutral-700 bg-neutral-900 p-3 shadow-xl group-hover:block">
            <p className="mb-2 text-xs font-medium text-neutral-400">Microphone</p>
            <select
              className="mb-3 w-full rounded-lg border border-neutral-700 bg-neutral-800 px-2 py-1.5 text-sm text-white"
              value={currentAudioId ?? ""}
              onChange={(e) => onSelectAudio(e.target.value)}
              disabled={selectingDevice}
            >
              {audioDevices.map((d) => (
                <option key={d.deviceId} value={d.deviceId}>
                  {d.label || "Microphone"}
                </option>
              ))}
            </select>
            <p className="mb-2 text-xs font-medium text-neutral-400">Camera</p>
            <select
              className="w-full rounded-lg border border-neutral-700 bg-neutral-800 px-2 py-1.5 text-sm text-white"
              value={currentVideoId ?? ""}
              onChange={(e) => onSelectVideo(e.target.value)}
              disabled={selectingDevice}
            >
              {videoDevices.map((d) => (
                <option key={d.deviceId} value={d.deviceId}>
                  {d.label || "Camera"}
                </option>
              ))}
            </select>
          </div>
        </div>
      ) : null}

      <div className="mx-1 h-8 w-px bg-neutral-700" />

      <Button
        type="button"
        variant="ghost"
        onClick={onLeave}
        aria-label={tMedia("leave")}
        className="size-11 rounded-full bg-red-500 text-white hover:bg-red-600"
      >
        <PhoneOff className="size-5" />
      </Button>
    </div>
  );
}
