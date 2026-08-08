"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { appDestinations } from "../../lib/liveroom-destinations";
import { liveroomSocket } from "../../lib/liveroom-socket";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { CHAT_MAX_CONTENT_LENGTH } from "../../types";
import { countCodePoints } from "../../utils/count-code-points";

const RECONCILE_TIMEOUT_MS = 10_000;

export function ChatComposer({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.chat");
  const [draft, setDraft] = useState("");
  const connected = useLiveroomStore((state) => state.connection.status === "connected");

  const used = countCodePoints(draft);
  const remaining = CHAT_MAX_CONTENT_LENGTH - used;
  const canSend = connected && used > 0 && remaining >= 0;

  const send = () => {
    if (!canSend) return;
    const content = draft;
    const tempId =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `${Date.now()}`;

    const store = useLiveroomStore.getState();
    store.addPendingChat({
      tempId,
      content,
      sentAt: new Date().toISOString(),
      failed: false,
    });
    liveroomSocket.publish(appDestinations.chatSend(roomId), { content });
    setDraft("");

    window.setTimeout(() => {
      const stillPending = useLiveroomStore
        .getState()
        .chat.pending.some((item) => item.tempId === tempId);
      if (stillPending) useLiveroomStore.getState().failPendingChat(tempId);
    }, RECONCILE_TIMEOUT_MS);
  };

  return (
    <div className="shrink-0 border-t border-neutral-200 p-2 dark:border-neutral-800">
      <div className="flex items-end gap-2">
        <label className="sr-only" htmlFor="liveroom-chat-input">
          {t("placeholder")}
        </label>
        <textarea
          id="liveroom-chat-input"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
          rows={1}
          placeholder={t("placeholder")}
          className="max-h-28 min-h-11 flex-1 resize-none rounded-lg border border-input bg-transparent px-2.5 py-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:min-h-9 md:py-1.5 dark:bg-input/30"
        />
        <Button
          size="icon"
          className="size-11 md:size-9"
          aria-label={t("send")}
          disabled={!canSend}
          onClick={send}
        >
          <Send className="size-4" />
        </Button>
      </div>
      {used > CHAT_MAX_CONTENT_LENGTH * 0.8 ? (
        <p
          className={`mt-1 text-right text-xs ${
            remaining < 0 ? "text-red-600 dark:text-red-400" : "text-neutral-500 dark:text-neutral-400"
          }`}
        >
          {t("charactersLeft", { count: remaining })}
        </p>
      ) : null}
    </div>
  );
}