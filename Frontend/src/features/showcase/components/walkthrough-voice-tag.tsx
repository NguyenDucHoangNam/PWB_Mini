"use client";

import { Mic } from "lucide-react";
import { WalkthroughModule, type ModuleSpec } from "./walkthrough-module";

const SPEC: ModuleSpec = {
  anchor: "voice-tag",
  namespace: "features.walkthrough.voiceTag",
  icon: Mic,
  specKeys: ["plan", "ways", "length", "formats"],
  next: { anchor: "song" },
  steps: [
    { key: "open", slot: "VT-01", factKeys: ["place", "plan"], note: true },
    { key: "create", slot: "VT-02" },
    {
      key: "fork",
      branches: [
        { key: "tts", slot: "VT-03", factKeys: ["name", "text", "language", "voice"] },
        { key: "upload", slot: "VT-04", factKeys: ["formats", "length", "size", "name"] },
      ],
    },
    { key: "preview", slot: "VT-05", note: true },
    { key: "save", slot: "VT-06", factKeys: ["result"] },
    { key: "manage", slot: "VT-07", factKeys: ["rename", "remove"], note: true },
  ],
};

export function WalkthroughVoiceTag() {
  return <WalkthroughModule spec={SPEC} />;
}
