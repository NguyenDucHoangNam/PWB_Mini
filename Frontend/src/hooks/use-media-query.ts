"use client";

import { useCallback, useSyncExternalStore } from "react";

/**
 * Reads a media query reactively.
 *
 * The server snapshot is `false` on purpose: callers use this to gate work that is too expensive
 * for the smallest screen, so the pre-hydration answer has to be the cheap branch. Guessing `true`
 * would render the heavy branch for one frame on every phone.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (callback: () => void) => {
      const media = window.matchMedia(query);
      media.addEventListener("change", callback);
      return () => media.removeEventListener("change", callback);
    },
    [query],
  );

  const getSnapshot = useCallback(() => window.matchMedia(query).matches, [query]);

  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
