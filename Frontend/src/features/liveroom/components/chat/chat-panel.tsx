"use client";

import { ChatComposer } from "./chat-composer";
import { ChatMessageList } from "./chat-message-list";

export function ChatPanel({ roomId }: { roomId: string }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ChatMessageList roomId={roomId} />
      <ChatComposer roomId={roomId} />
    </div>
  );
}