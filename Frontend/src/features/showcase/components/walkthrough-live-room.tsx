"use client";

import { Radio } from "lucide-react";
import { WalkthroughModule, type ModuleSpec } from "./walkthrough-module";

/**
 * No closing hand-off card: this is the last module, and the technical bridge right after it is what
 * the reader is handed to.
 */
const SPEC: ModuleSpec = {
  anchor: "live-room",
  namespace: "features.walkthrough.liveRoom",
  icon: Radio,
  specKeys: ["plan", "capacity", "code", "devices"],
  steps: [
    {
      key: "open",
      slot: "LR-01",
      media: { kind: "image", src: "/images/walkthrough/lr-01.png" },
      factKeys: ["place", "plan"],
      note: true,
    },
    {
      key: "fork",
      branches: [
        {
          key: "host",
          slot: "LR-02",
          media: { kind: "image", src: "/images/walkthrough/lr-02.png" },
          factKeys: ["name", "capacity", "grace", "code"],
        },
        {
          key: "guest",
          slot: "LR-03",
          media: { kind: "image", src: "/images/walkthrough/lr-03.png" },
          factKeys: ["code", "lookup", "devices"],
        },
      ],
    },
    {
      key: "devices",
      slot: "LR-03B",
      media: { kind: "image", src: "/images/walkthrough/lr-devices.png" },
      factKeys: ["preview", "camera", "mic"],
      note: true,
    },
    {
      key: "door",
      slot: "LR-04",
      media: { kind: "image", src: "/images/walkthrough/lr-04.png" },
      factKeys: ["approve", "decline", "returning"],
      note: true,
    },
    {
      key: "listen",
      slot: "LR-06",
      media: { kind: "image", src: "/images/walkthrough/lr-06.png" },
      factKeys: ["source", "sync", "comments"],
      note: true,
    },
    {
      key: "end",
      slot: "LR-08",
      media: { kind: "image", src: "/images/walkthrough/lr-08.png" },
      factKeys: ["leave", "grace", "reopen"],
      note: true,
    },
  ],
};

export function WalkthroughLiveRoom() {
  return <WalkthroughModule spec={SPEC} />;
}
