import { Fragment, type ReactNode } from "react";

const URL_PATTERN = /\b(https?:\/\/[^\s<>"']+)/gi;

/**
 * Returns React nodes, never an HTML string. Chat content is not escaped by the backend, so the
 * one rule that keeps it safe is that it never reaches `dangerouslySetInnerHTML`, a markdown
 * renderer, or anything else that turns a string into markup — React escapes text nodes for us.
 */
export function linkifyChat(content: string): ReactNode {
  const parts: ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;

  for (const match of content.matchAll(URL_PATTERN)) {
    const url = match[0];
    const start = match.index ?? 0;
    if (start > lastIndex) parts.push(content.slice(lastIndex, start));
    parts.push(
      <a
        key={`link-${key}`}
        href={url}
        target="_blank"
        rel="noopener noreferrer nofollow"
        className="underline underline-offset-2 hover:no-underline"
      >
        {url}
      </a>,
    );
    key += 1;
    lastIndex = start + url.length;
  }

  if (lastIndex < content.length) parts.push(content.slice(lastIndex));
  if (parts.length === 0) return content;

  return parts.map((part, index) => <Fragment key={index}>{part}</Fragment>);
}