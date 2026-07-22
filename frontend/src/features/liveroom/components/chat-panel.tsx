"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Send } from "lucide-react";

interface ChatPanelProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
}

interface ChatMessage {
  id: string;
  authorId: string;
  authorName: string;
  content: string;
  timestamp: string;
}

export function ChatPanel({ localUserId, localDisplayName }: ChatPanelProps) {
  const tImmersive = useTranslations("liveroom.immersive");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");

  const handleSend = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    const newMessage: ChatMessage = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      authorId: localUserId,
      authorName: localDisplayName,
      content: trimmed,
      timestamp: new Date().toISOString(),
    };
    setMessages((current) => [...current, newMessage]);
    setDraft("");
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      handleSend();
    }
  };

  const formatClock = (iso: string) => {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    return new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
    }).format(d);
  };

  return (
    <div className="flex h-full flex-col gap-3">
      <div className="flex-1 overflow-y-auto rounded-lg border border-white/5 bg-neutral-950/40 p-3">
        {messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 py-12 text-center text-sm text-neutral-500">
            <span>{tImmersive("chatEmpty")}</span>
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {messages.map((message) => {
              const isLocal = message.authorId === localUserId;
              return (
                <li
                  key={message.id}
                  className={`flex flex-col gap-1 rounded-lg px-3 py-2 ${
                    isLocal
                      ? "ml-6 bg-blue-600/20 text-white"
                      : "mr-6 bg-white/5 text-white"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2 text-[11px] text-neutral-400">
                    <span className="font-semibold text-neutral-200">
                      {message.authorName}
                    </span>
                    <span>{formatClock(message.timestamp)}</span>
                  </div>
                  <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">
                    {message.content}
                  </p>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <div className="flex items-end gap-2 rounded-lg border border-white/5 bg-neutral-950/40 p-2">
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={tImmersive("chatPlaceholder")}
          rows={1}
          className="flex-1 resize-none bg-transparent px-2 py-1.5 text-sm text-white placeholder:text-neutral-500 focus:outline-none"
        />
        <button
          type="button"
          onClick={handleSend}
          disabled={!draft.trim()}
          aria-label={tImmersive("chatSend")}
          className="inline-flex size-9 items-center justify-center rounded-md bg-blue-600 text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-neutral-700 disabled:text-neutral-500"
        >
          <Send className="size-4" />
        </button>
      </div>
    </div>
  );
}