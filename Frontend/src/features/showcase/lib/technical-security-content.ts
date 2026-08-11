/* Shape of the HTTP security chapter.

   Replaces the four-question topic card. The reader asked for two things the card could not do:
   a drawing of the layers a request passes through with the prose underneath it, and rate
   limiting explained as a subject of its own — what it is before how this system does it.

   Order: the chain first, because every later claim depends on knowing where each layer sits;
   then what rate limiting is; then how it is built here.

   Reference: docs/technical/infra-06-bao-mat-va-rate-limit.md, checked against the code — the
   security config in IAM, the JWT filter, the shared rate limit filter and service, the client
   address resolver, and the filter-order test in the bootstrap module. Two places where the doc
   and the code disagree, and the code won:

     - The doc's list of current limitations says there is no allowlist. There is: the filter
       skips a configured set of paths outright, defaulting to the health endpoints.
     - The doc's layer table gives the global bucket as one key per caller. The scope of the
       matched rule is part of the key too, so ordinary browsing and a tight endpoint rule no
       longer spend the same counter.

   Counts live here because next-intl has no array lookup: a list is read as numbered keys, and
   an entry added to one locale but forgotten in the other has to fail loudly. */

export const SECURITY_BLOCKS = ["chain", "what", "how"] as const;

export type SecurityBlock = (typeof SECURITY_BLOCKS)[number];

/** Layers in the chain, numbered in the order a request meets them. */
export const CHAIN_LAYERS = 5;

/* The two runs of layers, drawn as separate bands because the boundary between them is real:
   the first two are ordinary servlet filters that run ahead of everything, the last three are
   installed inside the security chain. Where a layer lives is what decides what it can see. */
export const CHAIN_GROUPS = [
  { id: "servlet", from: 0, count: 2 },
  { id: "security", from: 2, count: 3 },
] as const;

/* The rate limiter. Highlighted in the drawing because its position between the two layers
   around it is the single most consequential decision in this section. */
export const RATE_LIMIT_LAYER = 3;

/** The two ways the order can be got wrong, each silent. */
export const ORDER_RISKS = 2;

/** What rate limiting is there to stop. */
export const RISKS = 3;

/** Ceiling, exception, exemption — the three tiers of the HTTP limit. */
export const RULE_TIERS = 3;

/** What a caller is told, allowed or refused. */
export const SIGNALS = 2;

/** Rows in the table of every limiter in the system, HTTP and beyond. */
export const LIMITER_ROWS = 6;
