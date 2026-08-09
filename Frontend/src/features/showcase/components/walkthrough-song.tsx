"use client";

import { Music } from "lucide-react";
import { WalkthroughModule, type ModuleSpec } from "./walkthrough-module";

const SPEC: ModuleSpec = {
  anchor: "song",
  namespace: "features.walkthrough.song",
  icon: Music,
  // Raised, so the eye can see where module 01 ended and module 02 began.
  tone: "raised",
  specKeys: ["plan", "time", "formats", "tag"],
  next: { anchor: "live-room" },
  steps: [
    { key: "open", slot: "SG-01", factKeys: ["route", "filter"], note: true },
    { key: "file", slot: "SG-02", factKeys: ["formats", "size", "title"] },
    {
      key: "fork",
      branches: [
        { key: "plain", slot: "SG-03", factKeys: ["result", "status", "later"] },
        { key: "tagged", slot: "SG-04", factKeys: ["source", "search", "missing"] },
      ],
    },
    {
      key: "tune",
      slot: "SG-05",
      factKeys: ["interval", "volume", "ducking", "offset"],
      note: true,
    },
    { key: "send", slot: "SG-06", factKeys: ["phases", "cancel", "merge"], note: true },
    { key: "listen", slot: "SG-07", factKeys: ["waveform", "player", "config"], note: true },
    { key: "manage", slot: "SG-08", factKeys: ["rename", "search", "remove"], note: true },
  ],
};

export function WalkthroughSong() {
  return <WalkthroughModule spec={SPEC} />;
}
