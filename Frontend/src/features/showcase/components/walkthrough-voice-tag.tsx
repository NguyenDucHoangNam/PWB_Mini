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
    {
      key: "open",
      slot: "VT-01",
      media: { kind: "image", src: "/images/walkthrough/vt-01.png" },
      factKeys: ["place", "plan"],
      note: true,
    },
    {
      key: "create",
      slot: "VT-02",
      media: { kind: "image", src: "/images/walkthrough/vt-02.png" },
    },
    {
      key: "fork",
      branches: [
        {
          key: "tts",
          slot: "VT-03",
          media: { kind: "image", src: "/images/walkthrough/vt-03.png" },
          factKeys: ["name", "text", "language", "voice"],
        },
        {
          key: "upload",
          slot: "VT-04",
          media: { kind: "image", src: "/images/walkthrough/vt-04.png" },
          factKeys: ["formats", "length", "size", "name"],
        },
      ],
    },
    {
      key: "preview",
      slot: "VT-05",
      media: { kind: "image", src: "/images/walkthrough/vt-05.png" },
      note: true,
    },
    {
      key: "save",
      slot: "VT-06",
      media: { kind: "image", src: "/images/walkthrough/vt-06.png" },
      factKeys: ["result"],
    },
    {
      key: "manage",
      slot: "VT-07",
      media: { kind: "image", src: "/images/walkthrough/vt-07.png" },
      factKeys: ["rename", "remove"],
      note: true,
    },
  ],
};

export function WalkthroughVoiceTag() {
  return <WalkthroughModule spec={SPEC} />;
}
