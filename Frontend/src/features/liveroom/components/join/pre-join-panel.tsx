"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Camera, CameraOff, Loader2, Mic, MicOff } from "lucide-react";
import { NEU_TEXT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
import { DevicePermissionNotice } from "./device-permission-notice";
import { JoinStepIndicator } from "./join-step-indicator";
import { useAudioLevel } from "../../hooks/use-audio-level";
import type { LocalMediaState } from "../../hooks/use-local-media";

interface PreJoinPanelProps {
  media: LocalMediaState;
  submitting: boolean;
  onSubmit: () => void;
}

export function PreJoinPanel({ media, submitting, onSubmit }: PreJoinPanelProps) {
  const t = useTranslations("liveroom.prejoin");
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioLevel = useAudioLevel(media.micOn ? media.stream : null);

  useEffect(() => {
    const element = videoRef.current;
    if (!element) return;
    element.srcObject = media.cameraOn ? media.stream : null;
  }, [media.cameraOn, media.stream]);

  return (
    <div className="neu-raised flex h-full w-full flex-col gap-4 rounded-3xl border-none p-5 sm:p-6">
      <JoinStepIndicator current={2} />

      <div className="flex flex-col gap-1">
        <h2 className={`text-lg font-bold tracking-tight ${NEU_TEXT}`}>{t("title")}</h2>
        <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("subtitle")}</p>
      </div>

      <div className="neu-pressed relative min-h-24 w-full flex-1 overflow-hidden rounded-2xl border-none">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          aria-label={t("cameraPreview")}
          className={`size-full -scale-x-100 object-cover ${media.cameraOn ? "" : "hidden"}`}
        />
        {!media.cameraOn ? (
          <div
            className={`flex size-full flex-col items-center justify-center gap-2 text-xs font-medium ${NEU_TEXT_MUTED}`}
          >
            <CameraOff className="size-8" aria-hidden="true" />
            {t("cameraOff")}
          </div>
        ) : null}

        {media.micOn ? (
          <div className="neu-raised-sm absolute bottom-2.5 left-2.5 flex items-center gap-2 rounded-xl border-none px-2.5 py-1.5">
            <Mic className="size-3.5 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
            <span className="flex items-end gap-0.5" aria-hidden="true">
              {[1, 2, 3, 4].map((bar) => (
                <span
                  key={bar}
                  className={`h-3 w-1 rounded-full beat-16th transition-colors ease-hammer motion-reduce:transition-none ${
                    audioLevel >= bar
                      ? "bg-indigo-600 dark:bg-indigo-400"
                      : "bg-slate-400/40 dark:bg-slate-500/40"
                  }`}
                />
              ))}
            </span>
          </div>
        ) : null}
      </div>

      <div className="flex items-center justify-center gap-3">
        <NeuButton
          type="button"
          className={`flex-1 sm:flex-none ${media.cameraOn ? "neu-pressed text-indigo-600 dark:text-indigo-400" : ""}`}
          aria-pressed={media.cameraOn}
          disabled={media.requesting}
          onClick={() => (media.cameraOn ? media.disableCamera() : void media.enableCamera())}
        >
          {media.cameraOn ? <Camera className="size-4" /> : <CameraOff className="size-4" />}
          {media.cameraOn ? t("cameraOn") : t("cameraOff")}
        </NeuButton>
        <NeuButton
          type="button"
          className={`flex-1 sm:flex-none ${media.micOn ? "neu-pressed text-indigo-600 dark:text-indigo-400" : ""}`}
          aria-pressed={media.micOn}
          disabled={media.requesting}
          onClick={() => (media.micOn ? media.disableMic() : void media.enableMic())}
        >
          {media.micOn ? <Mic className="size-4" /> : <MicOff className="size-4" />}
          {media.micOn ? t("micOn") : t("micOff")}
        </NeuButton>
      </div>

      {media.requesting ? (
        <span
          className={`flex items-center justify-center gap-1.5 text-xs font-medium ${NEU_TEXT_MUTED}`}
        >
          <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          {t("requesting")}
        </span>
      ) : null}

      {media.error ? <DevicePermissionNotice kind={media.error} /> : null}

      <NeuButton
        type="button"
        variant="primary"
        size="lg"
        className="w-full"
        disabled={submitting}
        onClick={onSubmit}
      >
        {submitting ? (
          <Loader2 className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
        ) : null}
        {t("enterRoom")}
      </NeuButton>
    </div>
  );
}