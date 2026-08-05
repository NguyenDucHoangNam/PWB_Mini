"use client";

import { useTranslations } from "next-intl";
import { AvatarInitials } from "../ui/avatar-initials";
import { linkifyChat } from "../../utils/linkify-chat";
import { displayName } from "../../utils/participant-sort";
import type { ChatMessage } from "../../types";

function timeOf(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function ChatMessageItem({
  message,
  isMine,
  pending,
  failed,
  onRetry,
}: {
  message: ChatMessage;
  isMine: boolean;
  pending?: boolean;
  failed?: boolean;
  onRetry?: () => void;
}) {
  const t = useTranslations("liveroom.room.chat");
  const tParticipants = useTranslations("liveroom.room.participants");

  return (
    <li className={`flex gap-2 px-3 py-1.5 ${pending ? "opacity-60" : ""}`}>
      <AvatarInitials
        email={message.userEmail}
        seed={message.userId}
        className="mt-0.5 size-7 text-[10px]"
      />
      <div className="min-w-0 flex-1">
        <p className="flex items-baseline gap-2">
          <span className="truncate text-xs font-semibold text-black dark:text-white">
            {isMine ? tParticipants("you") : displayName(message)}
          </span>
          <span className="shrink-0 text-[10px] text-neutral-400">{timeOf(message.sentAt)}</span>
        </p>
        <p className="text-sm break-words whitespace-pre-wrap text-neutral-800 dark:text-neutral-200">
          {linkifyChat(message.content)}
        </p>
        {failed ? (
          <button
            type="button"
            onClick={onRetry}
            className="mt-0.5 text-xs text-red-600 underline underline-offset-2 dark:text-red-400"
          >
            {t("sendFailed")} · {t("retry")}
          </button>
        ) : null}
      </div>
    </li>
  );
}