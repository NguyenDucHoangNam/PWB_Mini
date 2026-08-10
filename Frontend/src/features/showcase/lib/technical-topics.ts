import { KeyRound, Radio, Server, Workflow, type LucideIcon } from "lucide-react";

/* Long-form explainers for the infrastructure topics that carry the most weight, plus the
   realtime transport. Each one answers the same four questions in the same order, because the
   reader arriving at the second topic should not have to work out where the answers live.

   Source material is docs/technical/ — infra-01 (outbox & Kafka), infra-02 (Redis),
   infra-06 (HTTP security & rate limiting) and 13 (realtime STOMP). */

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

export const OUTBOX_TOPIC: TopicSpec = {
  id: "outbox",
  icon: Workflow,
  bullets: { what: 3, why: 3, when: 2, how: 0 },
  visual: { kind: "flow", steps: 7 },
  rows: 0,
};

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

export const REALTIME_TOPIC: TopicSpec = {
  id: "realtime",
  icon: Radio,
  bullets: { what: 2, why: 4, when: 0, how: 3 },
  visual: { kind: "flow", steps: 5 },
  rows: 6,
};

export const INFRA_TOPICS: readonly TopicSpec[] = [OUTBOX_TOPIC, REDIS_TOPIC, SECURITY_TOPIC];
