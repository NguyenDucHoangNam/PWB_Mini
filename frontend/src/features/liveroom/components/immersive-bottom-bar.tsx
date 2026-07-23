"use client";

import { useTranslations } from "next-intl";
import { MessageSquare, Mic, MicOff, PhoneOff, Users, Video, VideoOff } from "lucide-react";
import { ScreenShareControl } from "./screenshare-control";
import { HandRaiseButton } from "./hand-raise-button";

export type ImmersivePanelTab = "people" | "chat" | null;

interface ImmersiveBottomBarProps {
  micMuted: boolean;
  cameraOff: boolean;
  pendingRequestCount: number;
  participantCount: number;
  unreadMessageCount: number;
  activeTab: ImmersivePanelTab;
  isSharingScreen: boolean;
  roomCode: string;
  localUserId: string;
  onToggleMic: () => void;
  onToggleCamera: () => void;
  onToggleScreenShare: () => void;
  onSelectTab: (tab: ImmersivePanelTab) => void;
  onLeave: () => void;
}

export function ImmersiveBottomBar({
  micMuted,
  cameraOff,
  pendingRequestCount,
  participantCount,
  unreadMessageCount,
  activeTab,
  isSharingScreen,
  roomCode,
  localUserId,
  onToggleMic,
  onToggleCamera,
  onToggleScreenShare,
  onSelectTab,
  onLeave,
}: ImmersiveBottomBarProps) {
  const tImmersive = useTranslations("liveroom.immersive");
  const tHost = useTranslations("liveroom.hostWaitingRoom");
  const tMedia = useTranslations("liveroom.media");

  const renderBadge = (count: number) =>
    count > 0 ? (
      <span className="absolute -right-1 -top-1 flex min-w-[18px] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
        {count > 9 ? "9+" : count}
      </span>
    ) : null;

  return (
    <div className="absolute inset-x-0 bottom-0 z-30 flex items-center justify-center bg-gradient-to-t from-black/80 via-black/40 to-transparent px-6 pb-6 pt-10">
      <div className="flex items-center gap-2 rounded-full bg-neutral-900/90 px-3 py-2 shadow-2xl backdrop-blur-md ring-1 ring-white/10">
        <button
          type="button"
          onClick={onToggleMic}
          aria-label={micMuted ? tMedia("micOff") : tMedia("micOn")}
          title={micMuted ? tMedia("micOff") : tMedia("micOn")}
          className={`inline-flex size-12 items-center justify-center rounded-full transition ${
            micMuted
              ? "bg-red-500 text-white hover:bg-red-600"
              : "bg-neutral-700 text-white hover:bg-neutral-600"
          }`}
        >
          {micMuted ? <MicOff className="size-5" /> : <Mic className="size-5" />}
        </button>

        <button
          type="button"
          onClick={onToggleCamera}
          aria-label={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
          title={cameraOff ? tMedia("cameraOff") : tMedia("cameraOn")}
          className={`inline-flex size-12 items-center justify-center rounded-full transition ${
            cameraOff
              ? "bg-red-500 text-white hover:bg-red-600"
              : "bg-neutral-700 text-white hover:bg-neutral-600"
          }`}
        >
          {cameraOff ? <VideoOff className="size-5" /> : <Video className="size-5" />}
        </button>

        <ScreenShareControl isSharing={isSharingScreen} onToggle={onToggleScreenShare} />

        <HandRaiseButton roomCode={roomCode} localUserId={localUserId} />

        <div className="mx-1 h-8 w-px bg-white/10" />

        <button
          type="button"
          onClick={() =>
            onSelectTab(activeTab === "people" ? null : "people")
          }
          aria-label={tImmersive("people")}
          title={`${tImmersive("people")} (${participantCount})`}
          aria-pressed={activeTab === "people"}
          className={`relative inline-flex size-12 items-center justify-center rounded-full transition ${
            activeTab === "people"
              ? "bg-blue-500 text-white"
              : "bg-neutral-700 text-white hover:bg-neutral-600"
          }`}
        >
          <Users className="size-5" />
          {renderBadge(participantCount)}
        </button>

        {pendingRequestCount > 0 ? (
          <span
            className="ml-1 inline-flex items-center gap-1 rounded-full bg-amber-500/20 px-2 py-1 text-[11px] font-medium text-amber-300"
            title={tHost("count", { count: pendingRequestCount })}
          >
            {tHost("count", { count: pendingRequestCount })}
          </span>
        ) : null}

        <button
          type="button"
          onClick={() => onSelectTab(activeTab === "chat" ? null : "chat")}
          aria-label={tImmersive("chat")}
          title={tImmersive("chat")}
          aria-pressed={activeTab === "chat"}
          className={`relative inline-flex size-12 items-center justify-center rounded-full transition ${
            activeTab === "chat"
              ? "bg-blue-500 text-white"
              : "bg-neutral-700 text-white hover:bg-neutral-600"
          }`}
        >
          <MessageSquare className="size-5" />
          {renderBadge(unreadMessageCount)}
        </button>

        <div className="mx-1 h-8 w-px bg-white/10" />

        <button
          type="button"
          onClick={onLeave}
          aria-label={tMedia("leave")}
          title={tMedia("leave")}
          className="inline-flex size-12 items-center justify-center rounded-full bg-red-500 text-white transition hover:bg-red-600"
        >
          <PhoneOff className="size-5" />
        </button>
      </div>
    </div>
  );
}
