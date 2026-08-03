"use client";

import { useEffect, useState } from "react";

export type ProfileTabId = "personal" | "security";

export function useProfileHashTab() {
  const [tab, setTab] = useState<ProfileTabId>("personal");

  useEffect(() => {
    if (typeof window === "undefined") return;
    const applyFromHash = () => {
      const next = window.location.hash.replace(/^#/, "");
      if (next === "security" || next === "personal") {
        setTab(next);
      }
    };
    applyFromHash();
    window.addEventListener("hashchange", applyFromHash);
    return () => window.removeEventListener("hashchange", applyFromHash);
  }, []);

  const setActiveTab = (next: ProfileTabId) => {
    setTab(next);
    if (typeof window !== "undefined") {
      const target = `#${next}`;
      if (window.location.hash !== target) {
        history.replaceState(null, "", target);
      }
    }
  };

  return [tab, setActiveTab] as const;
}
