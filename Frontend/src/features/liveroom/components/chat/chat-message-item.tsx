"use client";

import { useTranslations } from "next-intl";
import { NEU_TEXT, NEU_TEXT_MUTED, NEU_TEXT_SOFT } from "@/components/ui/neu";
import { UserAvatar } from "../ui/user-avatar";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
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
  const avatarUrl = useLiveroomStore(
    (state) => state.participants[message.userId]?.avatarUrl ?? null,
  );

  return (
    <li className={`flex gap-2 px-3 py-1.5 ${pending ? "opacity-60" : ""}`}>
      <UserAvatar
        email={message.userEmail}
        avatarUrl={avatarUrl}
        seed={message.userId}
        className="mt-0.5 size-7 text-[10px]"
      />
      <div className="min-w-0 flex-1">
        <p className="flex items-baseline gap-2">
          <span className={`truncate text-xs font-bold ${NEU_TEXT}`}>
            {isMine ? tParticipants("you") : displayName(message)}
          </span>
          <span className={`shrink-0 text-[10px] font-medium ${NEU_TEXT_MUTED}`}>{timeOf(message.sentAt)}</span>
        </p>
        <p className={`text-sm break-words whitespace-pre-wrap ${NEU_TEXT_SOFT}`}>
          {linkifyChat(message.content)}
        </p>
        {failed ? (
          <button
            type="button"
            onClick={onRetry}
            className="mt-1 text-xs font-semibold text-rose-700 underline underline-offset-2 dark:text-rose-400"
          >
            {t("sendFailed")} · {t("retry")}
          </button>
        ) : null}
      </div>
    </li>
  );
}