"use client";

import { Music } from "lucide-react";
import { WalkthroughModule, type ModuleSpec } from "./walkthrough-module";

const SPEC: ModuleSpec = {
  anchor: "song",
  namespace: "features.walkthrough.song",
  icon: Music,
  // Raised, so the eye can see where module 01 ended and module 02 began.
  tone: "raised",
  specKeys: ["plan", "formats", "tag", "result"],
  next: { anchor: "live-room" },
  steps: [
    {
      key: "open",
      slot: "SG-01",
      media: { kind: "image", src: "/images/walkthrough/sg-01.webp" },
      factKeys: ["place", "filter"],
      note: true,
    },
    {
      key: "file",
      slot: "SG-02",
      media: { kind: "image", src: "/images/walkthrough/sg-02.webp" },
      factKeys: ["formats", "size", "title"],
    },
    {
      key: "fork",
      branches: [
        {
          key: "plain",
          slot: "SG-03",
          media: { kind: "image", src: "/images/walkthrough/sg-03.webp" },
          factKeys: ["result", "status", "later"],
        },
        {
          key: "tagged",
          slot: "SG-04",
          media: { kind: "image", src: "/images/walkthrough/sg-04.webp" },
          factKeys: ["source", "search", "missing"],
        },
      ],
    },
    {
      key: "tune",
      slot: "SG-05",
      media: { kind: "image", src: "/images/walkthrough/sg-05.webp" },
      factKeys: ["interval", "volume", "ducking", "offset"],
      note: true,
    },
    {
      key: "send",
      slot: "SG-06",
      media: { kind: "image", src: "/images/walkthrough/sg-06.webp" },
      factKeys: ["phases", "cancel", "merge"],
      note: true,
    },
    {
      key: "listen",
      slot: "SG-07",
      media: { kind: "image", src: "/images/walkthrough/sg-07.webp" },
      factKeys: ["waveform", "player", "config"],
      note: true,
    },
    {
      key: "manage",
      slot: "SG-08",
      media: { kind: "image", src: "/images/walkthrough/sg-08.webp" },
      factKeys: ["rename", "search", "remove"],
      note: true,
    },
  ],
};

export function WalkthroughSong() {
  return <WalkthroughModule spec={SPEC} />;
}
