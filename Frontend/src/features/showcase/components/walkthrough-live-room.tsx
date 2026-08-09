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
    { key: "open", slot: "LR-01", factKeys: ["place", "plan"], note: true },
    {
      key: "fork",
      branches: [
        { key: "host", slot: "LR-02", factKeys: ["name", "capacity", "grace", "code"] },
        { key: "guest", slot: "LR-03", factKeys: ["code", "lookup", "devices"] },
      ],
    },
    { key: "door", slot: "LR-04", factKeys: ["approve", "decline", "returning"], note: true },
    { key: "inside", slot: "LR-05", factKeys: ["header", "grid", "panel"], note: true },
    { key: "listen", slot: "LR-06", factKeys: ["source", "sync", "comments"], note: true },
    { key: "moderate", slot: "LR-07", factKeys: ["remove", "mute", "micStates"], note: true },
    { key: "end", slot: "LR-08", factKeys: ["leave", "grace", "reopen"], note: true },
  ],
};

export function WalkthroughLiveRoom() {
  return <WalkthroughModule spec={SPEC} />;
}
