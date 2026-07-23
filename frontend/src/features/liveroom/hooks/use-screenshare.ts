"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";

export function useScreenShare(): {
  isSharing: boolean;
  stream: MediaStream | null;
  start: () => Promise<MediaStream | null>;
  stop: () => void;
  toggle: () => Promise<void>;
} {
  const t = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const [isSharing, setIsSharing] = useState(false);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const stop = useCallback(() => {
    const current = streamRef.current;
    if (current) {
      current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      setStream(null);
    }
    setIsSharing(false);
  }, []);

  const start = useCallback(async () => {
    if (typeof navigator === "undefined" || !navigator.mediaDevices?.getDisplayMedia) {
      toast.error(t("screenShareNotSupported"));
      return null;
    }
    try {
      const next = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: false,
      });
      const videoTrack = next.getVideoTracks()[0];
      if (videoTrack) {
        videoTrack.addEventListener("ended", () => {
          stop();
        });
      }
      streamRef.current = next;
      setStream(next);
      setIsSharing(true);
      return next;
    } catch (err) {
      const name = (err as { name?: string })?.name;
      if (name === "NotAllowedError") {
        toast.info(tCommon("loading"));
        return null;
      }
      toast.error(t("screenShareFailed"));
      return null;
    }
  }, [stop, t, tCommon]);

  const toggle = useCallback(async () => {
    if (isSharing) {
      stop();
    } else {
      await start();
    }
  }, [isSharing, start, stop]);

  useEffect(() => {
    return () => {
      stop();
    };
  }, [stop]);

  return { isSharing, stream, start, stop, toggle };
}
