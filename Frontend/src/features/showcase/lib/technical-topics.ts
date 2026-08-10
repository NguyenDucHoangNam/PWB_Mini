import {
  Cloud,
  KeyRound,
  Rocket,
  Server,
  Siren,
  type LucideIcon,
} from "lucide-react";

/* Long-form explainers for the infrastructure topics that carry the most weight. Each one
   answers the same four questions in the same order, because the reader arriving at the second
   topic should not have to work out where the answers live.

   Two subjects have outgrown this format and left it: the realtime layer and the event queue.
   Both had to argue why they exist before they could say what they are, and a
   what/why/when/how grid cannot carry an argument. They live in their own chapter files —
   technical-realtime-content.ts and technical-outbox-content.ts.

   Source material is docs/technical/ — infra-02 (Redis) and infra-06 (HTTP security and rate
   limiting). */

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

export const REDIS_TOPIC: TopicSpec = {
  id: "redis",
  icon: Server,
  bullets: { what: 2, why: 3, when: 3, how: 2 },
  visual: { kind: "cards", cards: 3 },
  rows: 5,
};

export const SECURITY_TOPIC: TopicSpec = {
  id: "security",
  icon: KeyRound,
  bullets: { what: 0, why: 3, when: 0, how: 3 },
  /* The third band is the rate limiter. It is highlighted because its position between the
     other two is the whole point of the section. */
  visual: { kind: "layers", layers: 5, highlight: 2 },
  rows: 6,
};

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

export const DEPLOY_TOPIC: TopicSpec = {
  id: "deploy",
  icon: Rocket,
  bullets: { what: 0, why: 3, when: 2, how: 3 },
  visual: { kind: "cards", cards: 3 },
  rows: 3,
};

/* Order matches the infrastructure card grid this replaced, so a reader who saw the old page
   finds the topics where the cards used to be. */
export const INFRA_TOPICS: readonly TopicSpec[] = [
  REDIS_TOPIC,
  SECURITY_TOPIC,
  DEPLOY_TOPIC,
];
