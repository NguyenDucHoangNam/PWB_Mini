"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
import { ChatMessageItem } from "./chat-message-item";
import { useChatHistoryPaging } from "../../hooks/use-chat-history";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

const STICK_THRESHOLD_PX = 80;

export function ChatMessageList({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.chat");
  const messages = useLiveroomStore((state) => state.chat.messages);
  const pending = useLiveroomStore((state) => state.chat.pending);
  const myUserId = useLiveroomStore((state) => state.myUserId);
  const { loadOlder, loading, hasMore } = useChatHistoryPaging(roomId);

  const containerRef = useRef<HTMLDivElement>(null);
  const stickRef = useRef(true);
  const [unread, setUnread] = useState(false);

  const handleScroll = () => {
    const element = containerRef.current;
    if (!element) return;
    const distance = element.scrollHeight - element.scrollTop - element.clientHeight;
    stickRef.current = distance <= STICK_THRESHOLD_PX;
    if (stickRef.current) setUnread(false);
  };

  useLayoutEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    if (stickRef.current) {
      element.scrollTop = element.scrollHeight;
      setUnread(false);
    } else {
      setUnread(true);
    }
  }, [messages.length, pending.length]);

  useEffect(() => {
    const element = containerRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, []);

  const jumpToLatest = () => {
    const element = containerRef.current;
    if (!element) return;
    element.scrollTop = element.scrollHeight;
    stickRef.current = true;
    setUnread(false);
  };

  return (
    <div className="relative flex min-h-0 flex-1 flex-col">
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="neu-scroll-thin flex-1 overflow-y-auto"
        aria-live="polite"
      >
        {hasMore ? (
          <div className="flex justify-center py-2">
            <NeuButton variant="ghost" size="sm" disabled={loading} onClick={() => void loadOlder()}>
              {loading ? t("loading") : t("loadOlder")}
            </NeuButton>
          </div>
        ) : null}

        {messages.length === 0 && pending.length === 0 ? (
          <p className={`px-3 py-8 text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
            {t("empty")}
          </p>
        ) : (
          <ul className="flex flex-col py-2">
            {messages.map((message) => (
              <ChatMessageItem
                key={message.id}
                message={message}
                isMine={message.userId === myUserId}
              />
            ))}
            {pending.map((item) => (
              <ChatMessageItem
                key={item.tempId}
                pending
                failed={item.failed}
                isMine
                message={{
                  id: item.tempId,
                  roomId,
                  cycleId: "",
                  userId: myUserId ?? "",
                  userEmail: "",
                  content: item.content,
                  sentAt: item.sentAt,
                }}
              />
            ))}
          </ul>
        )}
      </div>

      {unread ? (
        <button
          type="button"
          onClick={jumpToLatest}
          className="absolute inset-x-0 bottom-3 mx-auto w-fit rounded-full border-none bg-indigo-600 px-4 py-2 text-xs font-bold text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-indigo-500 dark:focus-visible:outline-indigo-400"
        >
          {t("newMessages")}
        </button>
      ) : null}
    </div>
  );
}