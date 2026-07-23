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

    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => {
      window.removeEventListener("pagehide", onPageHide);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, []);
}
