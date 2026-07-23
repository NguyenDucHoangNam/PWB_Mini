"use client";

import { useEffect } from "react";
import { mediaSessionController } from "../lib/media-session";

function releaseSessionAndReport(): void {
  mediaSessionController.releaseForUnload();
}

export function useMediaSessionLifecycle(): void {
  useEffect(() => {
    const onPageHide = () => {
      releaseSessionAndReport();
    };
    const onBeforeUnload = () => {
      releaseSessionAndReport();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        releaseSessionAndReport();
      }
    };

    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("beforeunload", onBeforeUnload);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      window.removeEventListener("pagehide", onPageHide);
      window.removeEventListener("beforeunload", onBeforeUnload);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);
}
