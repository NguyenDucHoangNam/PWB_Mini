"use client";

import { useTranslations } from "next-intl";

interface DevicePickerProps {
  audioDevices: { deviceId: string; label: string }[];
  videoDevices: { deviceId: string; label: string }[];
  currentAudioId: string | null;
  currentVideoId: string | null;
  disabled: boolean;
  onSelectAudio: (deviceId: string) => void;
  onSelectVideo: (deviceId: string) => void;
}

export function DevicePicker({
  audioDevices,
  videoDevices,
  currentAudioId,
  currentVideoId,
  disabled,
  onSelectAudio,
  onSelectVideo,
}: DevicePickerProps) {
  const tMedia = useTranslations("liveroom.media");

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-black">
      <h3 className="text-sm font-semibold text-black dark:text-white">{tMedia("devices")}</h3>
      <div className="flex flex-col gap-2">
        <label className="flex flex-col gap-1 text-xs text-neutral-500 dark:text-neutral-400">
          <span>{tMedia("selectMic")}</span>
          <select
            className="h-9 rounded-md border border-neutral-200 bg-white px-2 text-sm text-black disabled:opacity-60 dark:border-neutral-800 dark:bg-neutral-900 dark:text-white"
            value={currentAudioId ?? ""}
            disabled={disabled || audioDevices.length === 0}
            onChange={(event) => {
              if (event.target.value) onSelectAudio(event.target.value);
            }}
          >
            {audioDevices.length === 0 ? (
              <option value="">—</option>
            ) : (
              audioDevices.map((device, index) => (
                <option key={device.deviceId} value={device.deviceId}>
                  {device.label || `Microphone ${index + 1}`}
                </option>
              ))
            )}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs text-neutral-500 dark:text-neutral-400">
          <span>{tMedia("selectCamera")}</span>
          <select
            className="h-9 rounded-md border border-neutral-200 bg-white px-2 text-sm text-black disabled:opacity-60 dark:border-neutral-800 dark:bg-neutral-900 dark:text-white"
            value={currentVideoId ?? ""}
            disabled={disabled || videoDevices.length === 0}
            onChange={(event) => {
              if (event.target.value) onSelectVideo(event.target.value);
            }}
          >
            {videoDevices.length === 0 ? (
              <option value="">—</option>
            ) : (
              videoDevices.map((device, index) => (
                <option key={device.deviceId} value={device.deviceId}>
                  {device.label || `Camera ${index + 1}`}
                </option>
              ))
            )}
          </select>
        </label>
      </div>
    </div>
  );
}
