"use client";

import { useCallback, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { ExternalLink, Link2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { ProUpgradePrompt } from "@/features/liveroom/components/pro-upgrade-prompt";
import { RoomStatusBadge } from "@/features/liveroom/components/room-status-badge";
import { RoomModeBadge } from "@/features/liveroom/components/room-mode-badge";
import { UpdateRoomForm } from "@/features/liveroom/components/update-room-form";
import { ParticipantsList } from "@/features/liveroom/components/participants-list";
import { JoinRequestQueuePanel } from "@/features/liveroom/components/join-request-queue-panel";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import {
  useEndRoom,
  useRoom,
  useListJoinRequests,
  liveRoomJoinRequestsKey,
  liveRoomParticipantsKey,
  liveRoomKey,
} from "@/features/liveroom";
import { useLiveRoomRealtime } from "@/features/liveroom/hooks/use-live-room-realtime";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { asApiError } from "@/lib/api-client";
import { resolveLiveroomErrorMessage } from "@/features/liveroom/lib/resolve-liveroom-error-message";

type HostTab = "settings" | "waiting" | "listeners";

export default function LiveRoomDetailPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";
  const { isPro } = useProGuard();

  const tActions = useTranslations("liveroom.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tCard = useTranslations("liveroom.card");
  const tNav = useTranslations("liveroom.nav");
  const tHost = useTranslations("liveroom.hostWaitingRoom");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);
  const queryClient = useQueryClient();

  const { data: roomRes, isLoading, isError } = useRoom({ roomCode });
  const { data: pendingRes } = useListJoinRequests({ roomCode, status: "PENDING" });
  const pendingCount = pendingRes?.success && pendingRes.data ? pendingRes.data.length : 0;

  const [tab, setTab] = useState<HostTab>("settings");

  const invalidateHostQueries = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: liveRoomJoinRequestsKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
  }, [queryClient, roomCode]);

  useLiveRoomRealtime({
    roomCode,
    isHost: true,
    onJoinRequestCreated: (event) => {
      invalidateHostQueries();
      toast.info(tHost("hostToastNewRequest", { name: event.displayName }));
      setTab((current) => (current === "waiting" ? current : "waiting"));
    },
    onParticipantChanged: () => {
      invalidateHostQueries();
    },
  });

  const showError = (err: unknown) => {
    toast.error(
      resolveLiveroomErrorMessage(
        err,
        (k) => tErrors(k as never),
        (k) => tCommon(k as never),
      ),
    );
  };

  const { mutate: endRoomMutate } = useEndRoom({
    mutationConfig: {
      onError: asApiError((err) => showError(err)),
    },
  });

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
      </div>
    );
  }

  if (isError || !roomRes?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tErrors("roomNotFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const room = roomRes.data;
  const isHost = currentUserId === room.hostUserId;

  if (!isHost) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tErrors("notHost")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const isTerminal = room.status === "ENDED";

  const handleEnd = () => {
    endRoomMutate({ roomCode });
  };

  const handleOpenRoomInNewTab = () => {
    if (typeof window === "undefined") return;
    window.open(`/live-rooms/${room.roomCode}`, "_blank", "noopener,noreferrer");
  };

  const handleCopyShareLink = async () => {
    if (typeof window === "undefined") return;
    const origin = window.location.origin;
    const link = `${origin}/live-rooms/${room.roomCode}`;
    try {
      await navigator.clipboard.writeText(link);
      toast.success(tActions("linkCopied"));
    } catch {
      toast.error(tCommon("error"));
    }
  };

  const tabs: { id: HostTab; label: string; badge?: number }[] = [
    { id: "settings", label: tNav("settingsTab") },
    { id: "waiting", label: tHost("tabLabel"), badge: pendingCount },
    { id: "listeners", label: tNav("listenersTab") },
  ];

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
              {room.title}
            </h1>
            <RoomStatusBadge status={room.status} />
            <RoomModeBadge mode={room.mode} />
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span className="font-mono">{room.roomCode}</span>
            <span>
              {tCard("capacity", {
                current: room.currentParticipantCount,
                max: room.maxParticipants,
              })}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {!isTerminal && (
            <>
              <Button variant="outline" onClick={handleCopyShareLink}>
                <Link2 className="mr-1 inline size-4" />
                {tActions("copyLink")}
              </Button>
              <Button onClick={handleOpenRoomInNewTab}>
                <ExternalLink className="mr-1 inline size-4" />
                {tActions("openRoom")}
              </Button>
            </>
          )}
          <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
            {tActions("back")}
          </Button>
          {!isTerminal && (
            <Button variant="destructive" onClick={handleEnd}>
              {tCard("end")}
            </Button>
          )}
        </div>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-neutral-200 dark:border-neutral-800">
        {tabs.map((tabItem) => {
          const selected = tab === tabItem.id;
          return (
            <button
              key={tabItem.id}
              type="button"
              onClick={() => setTab(tabItem.id)}
              aria-pressed={selected}
              className={`flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-semibold transition-colors ${
                selected
                  ? "border-black text-black dark:border-white dark:text-white"
                  : "border-transparent text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
              }`}
            >
              {tabItem.label}
              {typeof tabItem.badge === "number" && tabItem.badge > 0 && (
                <span className="inline-flex min-w-[20px] items-center justify-center rounded-full bg-blue-600 px-1.5 text-[10px] font-bold text-white">
                  {tabItem.badge}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {tab === "settings" && (
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <h2 className="mb-4 text-sm font-semibold text-black dark:text-white">
            {tNav("settingsTab")}
          </h2>
          <UpdateRoomForm room={room} />
        </div>
      )}

      {tab === "waiting" && (
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <JoinRequestQueuePanel roomCode={room.roomCode} />
        </div>
      )}

      {tab === "listeners" && (
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
        </div>
      )}
    </div>
  );
}
