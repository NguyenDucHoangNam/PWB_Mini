import { Mic, Music, Radio } from "lucide-react";

/**
 * The three modules the walkthrough covers, in the order a producer meets them.
 *
 * `status` is what the page reads to decide whether a module is a link or a greyed-out row, so
 * publishing the next walkthrough is a one-word edit here rather than a change in three components.
 *
 * `steps` is a number rather than part of a translated string so the count in the index cannot drift
 * away from the steps actually rendered in two languages at once.
 */
export const WALKTHROUGH_MODULES = [
  { key: "voiceTag", anchor: "voice-tag", status: "ready", icon: Mic, steps: 6 },
  { key: "song", anchor: "song", status: "ready", icon: Music, steps: 7 },
  { key: "liveRoom", anchor: "live-room", status: "ready", icon: Radio, steps: 7 },
] as const;

export type WalkthroughModuleKey = (typeof WALKTHROUGH_MODULES)[number]["key"];
