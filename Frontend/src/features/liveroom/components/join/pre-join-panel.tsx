"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { Camera, CameraOff, Loader2, Mic, MicOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DevicePermissionNotice } from "./device-permission-notice";
import { JoinStepIndicator } from "./join-step-indicator";
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

  useEffect(() => {
    const element = videoRef.current;
    if (!element) return;
    element.srcObject = media.cameraOn ? media.stream : null;
  }, [media.cameraOn, media.stream]);

  return (
    <div className="flex flex-col gap-6">
      <JoinStepIndicator current={2} />

      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold text-black md:text-2xl dark:text-white">
          {t("title")}
        </h2>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      </div>

      <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-neutral-900">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          aria-label={t("cameraPreview")}
          className={`size-full object-cover ${media.cameraOn ? "" : "hidden"}`}
        />
        {!media.cameraOn ? (
          <div className="flex size-full items-center justify-center text-sm text-neutral-400">
            <CameraOff className="mr-2 size-5" aria-hidden />
            {t("cameraOff")}
          </div>
        ) : null}
      </div>

      {media.error ? <DevicePermissionNotice kind={media.error} /> : null}

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant={media.cameraOn ? "default" : "outline"}
          className="h-11 md:h-9"
          disabled={media.requesting}
          onClick={() => (media.cameraOn ? media.disableCamera() : void media.enableCamera())}
        >
          {media.cameraOn ? <Camera className="size-4" /> : <CameraOff className="size-4" />}
          {media.cameraOn ? t("cameraOn") : t("cameraOff")}
        </Button>
        <Button
          type="button"
          variant={media.micOn ? "default" : "outline"}
          className="h-11 md:h-9"
          disabled={media.requesting}
          onClick={() => (media.micOn ? media.disableMic() : void media.enableMic())}
        >
          {media.micOn ? <Mic className="size-4" /> : <MicOff className="size-4" />}
          {media.micOn ? t("micOn") : t("micOff")}
        </Button>
        {media.requesting ? (
          <span className="flex items-center gap-2 text-sm text-neutral-500 dark:text-neutral-400">
            <Loader2 className="size-4 animate-spin" aria-hidden />
            {t("requesting")}
          </span>
        ) : null}
      </div>

      <Button
        type="button"
        className="h-11 w-full md:h-9"
        disabled={submitting || disabled}
        onClick={onSubmit}
      >
        {submitting ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
        {t("enterRoom")}
      </Button>
    </div>
  );
}