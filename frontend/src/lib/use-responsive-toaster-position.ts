"use client";

import { useEffect, useState } from "react";
import type { ToasterProps } from "sonner";

export function useResponsiveToasterPosition(): ToasterProps["position"] {
  const [position, setPosition] = useState<ToasterProps["position"]>("top-right");

  useEffect(() => {
    const mql = window.matchMedia("(max-width: 640px)");
    const sync = () => setPosition(mql.matches ? "bottom-center" : "top-right");
    sync();
    mql.addEventListener("change", sync);
    return () => mql.removeEventListener("change", sync);
  }, []);

  return position;
}
