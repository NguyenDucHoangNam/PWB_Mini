/* Shape of the Redis chapter.

   Replaces the four-question topic card this section used to carry. That format asked what /
   why / when / how in four boxes of equal weight, and Redis does not divide that way: the
   reader has to feel the problem before "an in-memory key-value store" means anything, and the
   part worth remembering — the three jobs it actually does here, and the three it deliberately
   does not — was spread across two of the four boxes.

   Order: the data that does not belong in the main database, what Redis is, and the three jobs
   it does here. It stops at the third — what Redis is deliberately kept out of, and how each
   call site behaves during an outage, are operational questions rather than the explanation this
   section exists to give.

   Reference: docs/technical/infra-02-redis.md, checked against the code — the token manager, the
   IAM throttling adapter, the login attempt checker, the live room code-lookup throttle and the
   shared HTTP rate limit filter. Every number here comes from bootstrap's application.yml or a
   properties default, not from the doc. The doc still carries the outage policies and the full
   key table, which is where a reader who needs them should be sent.

   Counts live here because next-intl has no array lookup: a list is read as numbered keys, and
   an entry added to one locale but forgotten in the other has to fail loudly. */

export const REDIS_BLOCKS = ["problem", "what", "usage"] as const;

export type RedisBlock = (typeof REDIS_BLOCKS)[number];

/** Traits of the data that the main database is the wrong home for. */
export const PROBLEM_TRAITS = 3;

/** What Redis is, as three ideas rather than three nouns. */
export const REDIS_IDEAS = 3;

/* The three jobs Redis does in this system. Each one is presented with the same three facts,
   because the interesting thing is that they differ: the three jobs ask three different
   questions and therefore store three different shapes. */
export const USES = 3;

export const USE_FACTS = ["holds", "life", "how"] as const;
