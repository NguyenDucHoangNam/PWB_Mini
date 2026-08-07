export const CONTACT_EMAIL = "namnguyenduchoang@gmail.com";

export const TOPICS = ["support", "bug", "feedback", "other"] as const;

export type Topic = (typeof TOPICS)[number];

export const MESSAGE_MAX_LENGTH = 1200;

interface DraftInput {
  name: string;
  email: string;
  topicLabel: string;
  subject: string;
  message: string;
}

/** The plain-text body we hand to the mail client — and the same text the copy button puts on the
    clipboard, so a user without a configured mail app still has something to paste. */
export function buildDraftBody({ name, email, topicLabel, message }: DraftInput): string {
  return [`${name} <${email}>`, topicLabel, "", message].join("\n");
}

export function buildDraftSubject({ topicLabel, subject }: DraftInput): string {
  return `[PWB · ${topicLabel}] ${subject}`;
}

export function buildMailtoHref(input: DraftInput): string {
  const params = new URLSearchParams({
    subject: buildDraftSubject(input),
    body: buildDraftBody(input),
  });
  return `mailto:${CONTACT_EMAIL}?${params.toString()}`;
}
