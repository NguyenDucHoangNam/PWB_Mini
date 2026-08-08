"use client";

import { useEffect, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ChatPanel } from "../chat/chat-panel";
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
    </div>
  );
}

function PanelHeader({
  tab,
  onSelect,
  onClose,
}: {
  tab: SidePanelTab;
  onSelect: (next: SidePanelTab) => void;
  onClose: () => void;
}) {
  const t = useTranslations("liveroom.room.controls");
  const isOwner = useLiveroomStore((state) => state.isOwner);
  const participantCount = useLiveroomStore(
    (state) => Object.keys(state.participants).length,
  );
  const pendingCount = useLiveroomStore((state) => Object.keys(state.joinRequests).length);
  const waiting = isOwner ? pendingCount : 0;

  return (
    <div className="flex shrink-0 items-center border-b border-neutral-200 dark:border-neutral-800">
      <div role="tablist" className="flex min-w-0 flex-1">
        {(["participants", "chat"] as const).map((value) => (
          <button
            key={value}
            role="tab"
            type="button"
            aria-selected={tab === value}
            onClick={() => onSelect(value)}
            className={`flex h-12 flex-1 items-center justify-center gap-1.5 border-b-2 text-sm font-semibold transition-colors md:h-11 ${
              tab === value
                ? "border-black text-black dark:border-white dark:text-white"
                : "border-transparent text-neutral-500 hover:text-black dark:text-neutral-400 dark:hover:text-white"
            }`}
          >
            {t(value)}
            {value === "participants" ? (
              <>
                <span className="rounded-full bg-neutral-200 px-1.5 py-0.5 text-[10px] leading-none tabular-nums dark:bg-neutral-800">
                  {participantCount}
                </span>
                {waiting > 0 ? (
                  <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums">
                    {waiting > 99 ? "99+" : waiting}
                  </span>
                ) : null}
              </>
            ) : null}
          </button>
        ))}
      </div>
      <Button
        variant="ghost"
        size="icon"
        className="mr-1 size-9 shrink-0"
        aria-label={t("hidePanel")}
        onClick={onClose}
      >
        <X className="size-4" />
      </Button>
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

  if (!open) return null;

  const inline = (
    <aside className="hidden w-[320px] shrink-0 flex-col border-l border-neutral-200 lg:flex xl:w-[360px] dark:border-neutral-800">
      <PanelHeader tab={tab} onSelect={onTabChange} onClose={onClose} />
      <PanelBody roomId={roomId} tab={tab} />
    </aside>
  );

  const drawer = mounted
    ? createPortal(
        <div className="fixed inset-0 z-40 flex justify-end lg:hidden">
          <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
          <div
            role="dialog"
            aria-modal="true"
            className="relative flex h-dvh w-[92%] max-w-[420px] flex-col border-l border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black"
          >
            <PanelHeader tab={tab} onSelect={onTabChange} onClose={onClose} />
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