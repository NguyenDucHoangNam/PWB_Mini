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
    <div className="flex h-full w-full flex-col gap-3 rounded-xl border border-border bg-card p-5 shadow-xs sm:p-6">
      <JoinStepIndicator current={2} />

      <div className="flex flex-col gap-0.5">
        <h2 className="text-lg font-bold tracking-tight text-foreground">{t("title")}</h2>
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="relative min-h-24 w-full flex-1 overflow-hidden rounded-xl border border-border bg-muted">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          aria-label={t("cameraPreview")}
          className={`size-full -scale-x-100 object-cover ${media.cameraOn ? "" : "hidden"}`}
        />
        {!media.cameraOn ? (
          <div className="flex size-full flex-col items-center justify-center gap-2 text-xs text-muted-foreground">
            <CameraOff className="size-8" aria-hidden="true" />
            {t("cameraOff")}
          </div>
        ) : null}

        {media.micOn ? (
          <div className="absolute bottom-2 left-2 flex items-center gap-2 rounded-lg border border-border bg-card/85 px-2.5 py-1.5 backdrop-blur-sm">
            <Mic className="size-3.5 text-muted-foreground" aria-hidden="true" />
            <span className="flex items-end gap-0.5" aria-hidden="true">
              {[1, 2, 3, 4].map((bar) => (
                <span
                  key={bar}
                  className={`h-3 w-1 rounded-full beat-16th transition-colors ease-hammer ${
                    audioLevel >= bar ? "bg-foreground" : "bg-muted-foreground/30"
                  }`}
                />
              ))}
            </span>
          </div>
        ) : null}
      </div>

      <div className="flex items-center justify-center gap-2">
        <Button
          type="button"
          variant={media.cameraOn ? "default" : "outline"}
          className="h-10 flex-1 font-semibold sm:flex-none"
          disabled={media.requesting}
          onClick={() => (media.cameraOn ? media.disableCamera() : void media.enableCamera())}
        >
          {media.cameraOn ? <Camera className="size-4" /> : <CameraOff className="size-4" />}
          {media.cameraOn ? t("cameraOn") : t("cameraOff")}
        </Button>
        <Button
          type="button"
          variant={media.micOn ? "default" : "outline"}
          className="h-10 flex-1 font-semibold sm:flex-none"
          disabled={media.requesting}
          onClick={() => (media.micOn ? media.disableMic() : void media.enableMic())}
        >
          {media.micOn ? <Mic className="size-4" /> : <MicOff className="size-4" />}
          {media.micOn ? t("micOn") : t("micOff")}
        </Button>
      </div>

      {media.requesting ? (
        <span className="flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
          <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
          {t("requesting")}
        </span>
      ) : null}

      {media.error ? <DevicePermissionNotice kind={media.error} /> : null}

      <Button
        type="button"
        className="h-11 w-full font-semibold"
        disabled={submitting}
        onClick={onSubmit}
      >
        {submitting ? <Loader2 className="mr-1.5 size-4 animate-spin" aria-hidden="true" /> : null}
        {t("enterRoom")}
      </Button>
    </div>
  );
}