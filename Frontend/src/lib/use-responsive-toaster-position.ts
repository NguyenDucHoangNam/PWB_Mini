"use client";

import { useEffect, useState } from "react";
import type { ToasterProps } from "sonner";

const MOBILE_QUERY = "(max-width: 639px)";
const TABLET_QUERY = "(min-width: 640px) and (max-width: 1023px)";

type ToasterPosition = ToasterProps["position"];

function resolvePosition(mobile: boolean, tablet: boolean): ToasterPosition {
  if (mobile) return "top-center";
  if (tablet) return "top-center";
  return "top-right";
}

export function useResponsiveToasterPosition(): ToasterPosition {
  const [position, setPosition] = useState<ToasterPosition>("top-right");

  useEffect(() => {
    if (typeof window === "undefined") return;

    const mobileQuery = window.matchMedia(MOBILE_QUERY);
    const tabletQuery = window.matchMedia(TABLET_QUERY);

    const sync = () => {
      setPosition(resolvePosition(mobileQuery.matches, tabletQuery.matches));
    };

    sync();

    mobileQuery.addEventListener("change", sync);
    tabletQuery.addEventListener("change", sync);

    return () => {
      mobileQuery.removeEventListener("change", sync);
      tabletQuery.removeEventListener("change", sync);
    };
  }, []);

  return position;
}