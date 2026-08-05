"use client";

import { useCallback, useState } from "react";
import { getChatHistory } from "../api/chat";
import { useLiveroomStore } from "../stores/use-liveroom-store";

export function useChatHistoryPaging(roomId: string) {
  const [loading, setLoading] = useState(false);
  const hasMore = useLiveroomStore((state) => state.chat.hasMore);
  const nextCursor = useLiveroomStore((state) => state.chat.nextCursor);

  const loadOlder = useCallback(async () => {
    if (!hasMore || !nextCursor || loading) return;
    setLoading(true);
    try {
      const response = await getChatHistory({ roomId, cursor: nextCursor });
      const page = response.data;
      if (page) {
        useLiveroomStore
          .getState()
          .prependChatHistory([...page.messages].reverse(), page.hasMore, page.nextCursor);
      }
    } finally {
      setLoading(false);
    }
  }, [roomId, hasMore, nextCursor, loading]);

  return { loadOlder, loading, hasMore };
}