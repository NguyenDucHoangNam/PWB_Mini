/* Shape of the event-queue chapter.

   Order set by what the reader needs, and it is not the order the code was written in: Kafka
   first on its own, then the problem Kafka alone does not solve, then the outbox that closes
   it. Introducing the outbox first — the way the reference doc does — means describing a fix
   for a problem the reader has not met yet.

   Reference: docs/technical/infra-01-outbox-va-kafka.md, checked against the code in
   shared-infrastructure. Where the two disagreed the code won. */

export const OUTBOX_BLOCKS = ["waiting", "kafka", "dualwrite", "outbox", "system"] as const;

export type OutboxBlock = (typeof OUTBOX_BLOCKS)[number];

/** Jobs in the system that must not be done while the user waits. */
export const SLOW_JOBS = 2;

/** What Kafka gives you, as three ideas rather than three nouns. */
export const KAFKA_IDEAS = 3;

/** The two ways of sending without an outbox, both wrong. */
export const DUAL_WRITE_OPTIONS = 2;

/** What the relay has to get right. */
export const RELAY_RULES = 3;

/** Stages of a registration, from the button to the inbox. */
export const FLOW_STEPS = 6;

/* The two asynchronous lanes in production. Topic names are identifiers — identical in both
   locales, so they live here rather than being duplicated across two bundles, and a brace in a
   message would be read as an ICU placeholder besides. */
export const LANES: readonly string[] = ["notification.email.v1", "voice.processing.v1"];
