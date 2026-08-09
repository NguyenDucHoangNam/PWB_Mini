"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Send } from "lucide-react";
import { NEU_INPUT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
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
    <div className="shrink-0 p-2.5">
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
          className={`${NEU_INPUT} max-h-28 min-h-11 flex-1 resize-none py-3 md:min-h-11`}
        />
        <NeuButton
          variant="primary"
          size="icon"
          aria-label={t("send")}
          disabled={!canSend}
          onClick={send}
        >
          <Send className="size-4" />
        </NeuButton>
      </div>
      {used > CHAT_MAX_CONTENT_LENGTH * 0.8 ? (
        <p
          className={`mt-1.5 text-right text-xs font-semibold ${
            remaining < 0 ? "text-rose-700 dark:text-rose-400" : NEU_TEXT_MUTED
          }`}
        >
          {t("charactersLeft", { count: remaining })}
        </p>
      ) : null}
    </div>
  );
}