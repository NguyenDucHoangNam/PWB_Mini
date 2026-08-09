import { Mic, Music, Radio } from "lucide-react";

/**
 * The three modules the walkthrough covers, in the order a producer meets them.
 *
 * `status` is what the page reads to decide whether a module is a link or a greyed-out row, so
 * publishing the next walkthrough is a one-word edit here rather than a change in three components.
 *
 * `steps` and `minutes` live here rather than inside a translated string so the hero can add them up
 * for its tally without the two numbers drifting apart in two languages.
 */
export const WALKTHROUGH_MODULES = [
  { key: "voiceTag", anchor: "voice-tag", status: "ready", icon: Mic, steps: 6, minutes: 3 },
  { key: "song", anchor: "song", status: "ready", icon: Music, steps: 7, minutes: 5 },
  { key: "liveRoom", anchor: "live-room", status: "ready", icon: Radio, steps: 7, minutes: 6 },
] as const;

export type WalkthroughModuleKey = (typeof WALKTHROUGH_MODULES)[number]["key"];

/** What the hero prints above the index: the size of the whole read, in one line. */
export const WALKTHROUGH_TALLY = WALKTHROUGH_MODULES.reduce(
  (total, module) => ({
    modules: total.modules + 1,
    steps: total.steps + module.steps,
    minutes: total.minutes + module.minutes,
  }),
  { modules: 0, steps: 0, minutes: 0 },
);
