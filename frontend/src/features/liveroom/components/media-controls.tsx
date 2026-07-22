"use client";

import { LogOut, Mic, MicOff, PhoneOff, Video, VideoOff } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { DevicePicker } from "./device-picker";

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
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-center gap-2 rounded-xl border border-neutral-200 bg-white px-4 py-3 shadow-sm dark:border-neutral-800 dark:bg-black">
        <Button
          type="button"
          variant={micMuted ? "destructive" : "outline"}
          size="icon"
          onClick={onToggleMic}
          aria-label={micMuted ? tMedia("micOff") : tMedia("micOn")}
          title={micMuted ? tMedia("micOff") : tMedia("micOn")}
        >
          {micMuted ? <MicOff className="size-4" /> : <Mic className="size-4" />}
        </Button>
        <Button
          type="button"
          variant={cameraOff ? "destructive" : "outline"}
          size="icon"
          onClick={onToggleCamera}
          aria-label={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
          title={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
        >
          {cameraOff ? <VideoOff className="size-4" /> : <Video className="size-4" />}
        </Button>
        <Button
          type="button"
          variant="destructive"
          onClick={onLeave}
          aria-label={tMedia("leave")}
          className="ml-2"
        >
          {cameraOff ? <PhoneOff className="mr-2 size-4" /> : <LogOut className="mr-2 size-4" />}
          {tMedia("leave")}
        </Button>
      </div>
      <DevicePicker
        audioDevices={audioDevices}
        videoDevices={videoDevices}
        currentAudioId={currentAudioId}
        currentVideoId={currentVideoId}
        disabled={selectingDevice}
        onSelectAudio={onSelectAudio}
        onSelectVideo={onSelectVideo}
      />
    </div>
  );
}
