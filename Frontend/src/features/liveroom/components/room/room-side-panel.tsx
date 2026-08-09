"use client";

import { useEffect, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { useTranslations } from "next-intl";
import { X } from "lucide-react";
import { NeuButton } from "@/components/ui/neu";
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
    <div className="neu-pressed-sm m-2 flex shrink-0 items-center rounded-2xl border-none p-1.5">
      <div role="tablist" className="flex min-w-0 flex-1">
        {(["participants", "chat"] as const).map((value) => (
          <button
            key={value}
            role="tab"
            type="button"
            aria-selected={tab === value}
            onClick={() => onSelect(value)}
            className={`flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border-none text-sm font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 md:h-10 dark:focus-visible:outline-indigo-400 ${
              tab === value
                ? "neu-raised-sm text-indigo-600 dark:text-indigo-400"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            }`}
          >
            {t(value)}
            {value === "participants" ? (
              <>
                <span className="neu-pressed-sm rounded-full border-none px-2 py-0.5 text-[10px] leading-none font-bold tabular-nums">
                  {participantCount}
                </span>
                {waiting > 0 ? (
                  <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums dark:bg-rose-500">
                    {waiting > 99 ? "99+" : waiting}
                  </span>
                ) : null}
              </>
            ) : null}
          </button>
        ))}
      </div>
      <NeuButton
        variant="ghost"
        size="icon-sm"
        className="shrink-0"
        aria-label={t("hidePanel")}
        onClick={onClose}
      >
        <X className="size-4" />
      </NeuButton>
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
    <aside className="neu-raised m-3 ml-0 hidden w-[320px] shrink-0 flex-col rounded-2xl border-none lg:flex xl:w-[360px]">
      <PanelHeader tab={tab} onSelect={onTabChange} onClose={onClose} />
      <PanelBody roomId={roomId} tab={tab} />
    </aside>
  );

  const drawer = mounted
    ? createPortal(
        <div className="fixed inset-0 z-40 flex justify-end lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={onClose} aria-hidden />
          <div
            role="dialog"
            aria-modal="true"
            className="relative flex h-dvh w-[92%] max-w-[420px] flex-col border-none bg-[#e0e5ec] dark:bg-[#1e222b]"
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