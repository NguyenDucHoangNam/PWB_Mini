/* Shape of the realtime section.

   This section teaches rather than documents. It follows the order a reader actually needs:
   feel the problem, meet the connection that solves it, understand why a second protocol sits
   on top of that connection, learn the delivery model, and only then look at what was built
   here. docs/technical/13-realtime-stomp.md is the reference behind it, not its outline — that
   doc is written for someone who already knows STOMP, and reading it front to back is exactly
   what made the previous version of this page impossible to follow.

   Counts live here because next-intl has no array lookup: a list is read as numbered keys, and
   an entry added to one locale but forgotten in the other has to fail loudly. */

export const REALTIME_BLOCKS = ["problem", "websocket", "stomp", "pubsub", "system"] as const;

export type RealtimeBlock = (typeof REALTIME_BLOCKS)[number];

/* One STOMP message, printed. It appears once, in the section that argues STOMP is a set of
   text conventions — because that claim is abstract until you see that a "convention" here is
   literally a word on the first line and a channel name on the second.

   Not in the message bundles: a protocol keyword is not language, and a translated frame would
   misrepresent what crosses the wire. Shortened room id for the same reason the rest of the
   page rounds numbers — the full UUID teaches nothing and wraps on a phone. */
export const SAMPLE_FRAME = [
  "SEND",
  "destination:/topic/liveroom/3f2a…/music",
  "content-type:application/json",
  "",
  '{"action":"play","songId":"9c11…"}',
].join("\n");

/** Paragraphs under "the problem with request–response". */
export const PROBLEM_POINTS = 3;

/** What a bare WebSocket gives you. */
export const WEBSOCKET_POINTS = 4;

/** The jobs STOMP takes off your hands. */
export const STOMP_GAPS = 3;

/** The two kinds of channel in the pub/sub model. */
export const PUBSUB_KINDS = 2;

/* Stages in the flow that was built here. Deliberately a flow and not a component list: the
   question this chapter answers is "what happens, in what order", and naming classes would
   answer a question nobody asked. */
export const FLOW_STEPS = 6;

/** Rows in the channel map. */
export const CHANNELS: readonly string[] = [
  "/topic/liveroom/{id}",
  "/topic/liveroom/{id}/chat",
  "/topic/liveroom/{id}/music",
  "/user/queue/liveroom",
  "/user/queue/liveroom/rtc",
  "/user/queue/liveroom/errors",
];
