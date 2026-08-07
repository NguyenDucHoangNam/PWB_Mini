"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Camera, CameraOff, Loader2, Mic, MicOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DevicePermissionNotice } from "./device-permission-notice";
import { JoinStepIndicator } from "./join-step-indicator";
import { useAudioLevel } from "../../hooks/use-audio-level";
import type { LocalMediaState } from "../../hooks/use-local-media";

interface PreJoinPanelProps {
  media: LocalMediaState;
  submitting: boolean;
  disabled?: boolean;
  onSubmit: () => void;
}

export function PreJoinPanel({ media, submitting, disabled, onSubmit }: PreJoinPanelProps) {
  const t = useTranslations("liveroom.prejoin");
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioLevel = useAudioLevel(media.micOn ? media.stream : null);

  useEffect(() => {
    const element = videoRef.current;
    if (!element) return;
    element.srcObject = media.cameraOn ? media.stream : null;
  }, [media.cameraOn, media.stream]);

  return (
    <div className="grid grid-cols-1 md:grid-cols-12 gap-5 items-start w-full">
      <div className="md:col-span-5 flex flex-col justify-between gap-4 rounded-xl border border-neutral-300 bg-white p-5 shadow-xs dark:border-neutral-800 dark:bg-black">
        <JoinStepIndicator current={2} />

        <div className="flex flex-col gap-0.5">
          <h2 className="text-lg font-bold tracking-tight text-neutral-900 md:text-xl dark:text-neutral-100">
            {t("title")}
          </h2>
          <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
        </div>

        {media.micOn ? (
          <div className="flex items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 p-2.5 dark:border-neutral-800 dark:bg-neutral-900">
            <span className="font-mono text-xs font-semibold text-neutral-600 dark:text-neutral-400">
              {t("micPreview")}
            </span>
            <span className="flex flex-1 items-end gap-1" aria-hidden="true">
              {[1, 2, 3, 4].map((bar) => (
                <span
                  key={bar}
                  className={`h-2.5 flex-1 rounded-full transition-colors ${
                    audioLevel >= bar
                      ? "bg-black dark:bg-white"
                      : "bg-neutral-300 dark:bg-neutral-700"
                  }`}
                />
              ))}
            </span>
          </div>
        ) : null}

        {media.error ? <DevicePermissionNotice kind={media.error} /> : null}

        <div className="flex flex-wrap gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            className={`h-9 font-mono text-xs font-bold ${
              media.cameraOn
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300"
            }`}
            disabled={media.requesting}
            onClick={() => (media.cameraOn ? media.disableCamera() : void media.enableCamera())}
          >
            {media.cameraOn ? <Camera className="size-4 mr-1" /> : <CameraOff className="size-4 mr-1" />}
            {media.cameraOn ? t("cameraOn") : t("cameraOff")}
          </Button>
          <Button
            type="button"
            variant="outline"
            className={`h-9 font-mono text-xs font-bold ${
              media.micOn
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300"
            }`}
            disabled={media.requesting}
            onClick={() => (media.micOn ? media.disableMic() : void media.enableMic())}
          >
            {media.micOn ? <Mic className="size-4 mr-1" /> : <MicOff className="size-4 mr-1" />}
            {media.micOn ? t("micOn") : t("micOff")}
          </Button>
          {media.requesting ? (
            <span className="flex items-center gap-1.5 font-mono text-xs text-neutral-500 dark:text-neutral-400">
              <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
              {t("requesting")}
            </span>
          ) : null}
        </div>

        <Button
          type="button"
          className="h-10 min-h-[44px] sm:min-h-0 w-full bg-black font-semibold text-white hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200"
          disabled={submitting || disabled}
          onClick={onSubmit}
        >
          {submitting ? <Loader2 className="size-4 animate-spin mr-1.5" aria-hidden="true" /> : null}
          {t("enterRoom")}
        </Button>
      </div>

      <div className="md:col-span-7 relative aspect-video w-full overflow-hidden rounded-xl border border-neutral-300 bg-black dark:border-neutral-800 shadow-xs min-h-[220px] max-h-[360px]">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          aria-label={t("cameraPreview")}
          className={`size-full -scale-x-100 object-cover ${media.cameraOn ? "" : "hidden"}`}
        />
        {!media.cameraOn ? (
          <div className="flex size-full flex-col items-center justify-center gap-2 text-xs font-mono text-neutral-400">
            <CameraOff className="size-8 text-neutral-600 dark:text-neutral-500" aria-hidden="true" />
            {t("cameraOff")}
          </div>
        ) : null}
      </div>
    </div>
  );
}