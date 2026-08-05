"use client";

import { useEffect, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ChatPanel } from "../chat/chat-panel";
import { MusicPlayer } from "../music/music-player";
import { JoinRequestQueue } from "../participants/join-request-queue";
import { ParticipantList } from "../participants/participant-list";
import { useLiveroomStore } from "../../stores/use-liveroom-store";

export type SidePanelTab = "participants" | "chat";

const subscribeNever = () => () => {};
const getMounted = () => true;
const getServerMounted = () => false;

function PanelBody({ roomId, tab }: { roomId: string; tab: SidePanelTab }) {
  const isOwner = useLiveroomStore((state) => state.isOwner);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {tab === "participants" ? (
        <>
          {isOwner ? <JoinRequestQueue roomId={roomId} /> : null}
          <ParticipantList roomId={roomId} />
        </>
      ) : (
        <ChatPanel roomId={roomId} />
      )}
      <MusicPlayer roomId={roomId} />
    </div>
  );
}

function PanelTabs({
  tab,
  onSelect,
}: {
  tab: SidePanelTab;
  onSelect: (next: SidePanelTab) => void;
}) {
  const t = useTranslations("liveroom.room.controls");

  return (
    <div role="tablist" className="flex shrink-0 border-b border-neutral-200 dark:border-neutral-800">
      {(["participants", "chat"] as const).map((value) => (
        <button
          key={value}
          role="tab"
          type="button"
          aria-selected={tab === value}
          onClick={() => onSelect(value)}
          className={`h-12 flex-1 border-b-2 text-sm font-semibold transition-colors md:h-11 ${
            tab === value
              ? "border-black text-black dark:border-white dark:text-white"
              : "border-transparent text-neutral-500 hover:text-black dark:text-neutral-400 dark:hover:text-white"
          }`}
        >
          {t(value)}
        </button>
      ))}
    </div>
  );
}

export function RoomSidePanel({
  roomId,
  open,
  tab,
  onTabChange,
  onClose,
}: {
  roomId: string;
  open: boolean;
  tab: SidePanelTab;
  onTabChange: (next: SidePanelTab) => void;
  onClose: () => void;
}) {
  const mounted = useSyncExternalStore(subscribeNever, getMounted, getServerMounted);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const inline = (
    <aside className="hidden w-[320px] shrink-0 flex-col border-l border-neutral-200 lg:flex xl:w-[360px] dark:border-neutral-800">
      <PanelTabs tab={tab} onSelect={onTabChange} />
      <PanelBody roomId={roomId} tab={tab} />
    </aside>
  );

  const drawer =
    mounted && open
      ? createPortal(
          <div className="fixed inset-0 z-40 flex justify-end lg:hidden">
            <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
            <div
              role="dialog"
              aria-modal="true"
              className="relative flex h-dvh w-[92%] max-w-[420px] flex-col border-l border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black"
            >
              <div className="flex shrink-0 items-center justify-end p-1">
                <Button variant="ghost" size="icon" className="size-11" onClick={onClose}>
                  <X className="size-5" />
                </Button>
              </div>
              <PanelTabs tab={tab} onSelect={onTabChange} />
              <PanelBody roomId={roomId} tab={tab} />
            </div>
          </div>,
          document.body,
        )
      : null;

  return (
    <>
      {inline}
      {drawer}
    </>
  );
}