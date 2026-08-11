import { Cloud, Siren, type LucideIcon } from "lucide-react";

/* Long-form explainers for the infrastructure topics that carry the most weight. Each one
   answers the same four questions in the same order, because the reader arriving at the second
   topic should not have to work out where the answers live.

   Four subjects have outgrown this format and left it: the realtime layer, the event queue,
   Redis, and HTTP security. All four had to argue something before they could describe anything
   — why they exist, or why one layer sits where it does — and a what/why/when/how grid cannot
   carry an argument. They live in their own chapter files, one -content.ts each. */

export const TOPIC_QUESTIONS = ["what", "why", "when", "how"] as const;
export type TopicQuestion = (typeof TOPIC_QUESTIONS)[number];

/* One visual per topic, chosen by the shape of the thing being explained rather than for
   variety: a pipeline gets a flow, a stack of filters gets bands, a set of parallel choices
   gets cards. All three reflow on a narrow screen — unlike the architecture diagrams, none of
   this content is a two-dimensional graph, so none of it needs a fixed canvas. */
export type TopicVisual =
  | { kind: "flow"; steps: number }
  | { kind: "layers"; layers: number; highlight: number }
  | { kind: "cards"; cards: number };

export interface TopicSpec {
  /** Message key under `features.technical.topics`, and the anchor id. */
  id: string;
  icon: LucideIcon;
  /** Bullet count under each question. Zero means the paragraph stands alone. */
  bullets: Record<TopicQuestion, number>;
  visual: TopicVisual;
  /** Rows in the closing table. Zero means the topic has none. */
  rows: number;
}

export const STORAGE_TOPIC: TopicSpec = {
  id: "storage",
  icon: Cloud,
  bullets: { what: 2, why: 3, when: 3, how: 3 },
  visual: { kind: "cards", cards: 3 },
  rows: 5,
};

export const ERRORS_TOPIC: TopicSpec = {
  id: "errors",
  icon: Siren,
  bullets: { what: 3, why: 0, when: 2, how: 3 },
  visual: { kind: "flow", steps: 5 },
  rows: 8,
};

/* Whatever is still on the card format, rendered after the chapters. */
export const INFRA_TOPICS: readonly TopicSpec[] = [];
