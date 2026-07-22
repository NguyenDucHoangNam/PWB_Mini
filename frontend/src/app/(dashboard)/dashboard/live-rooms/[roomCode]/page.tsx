"use client";

import { useCallback, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { ExternalLink, Link2, Users, UserPlus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { JoinRequestQueuePanel } from "@/features/liveroom/components/join-request-queue-panel";
import { ParticipantsList } from "@/features/liveroom/components/participants-list";
import { MediaStage } from "@/features/liveroom/components/media-stage";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import {
  useEndRoom,
  useLeaveRoom,
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

type SidebarTab = "requests" | "participants" | null;

export default function LiveRoomDetailPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";
  const { isPro } = useProGuard();

  const tActions = useTranslations("liveroom.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tCard = useTranslations("liveroom.card");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);
  const queryClient = useQueryClient();
  const localDisplayName = useAuthStore((state) => state.user?.username ?? "Host");

  const { data: roomRes, isLoading, isError } = useRoom({ roomCode });
  const { data: pendingRes } = useListJoinRequests({ roomCode, status: "PENDING" });
  const pendingCount = pendingRes?.success && pendingRes.data ? pendingRes.data.length : 0;

  const [sidebarTab, setSidebarTab] = useState<"requests" | "participants" | null>(null);

  const invalidateHostQueries = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: liveRoomJoinRequestsKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
  }, [queryClient, roomCode]);

  useLiveRoomRealtime({
    roomCode,
    isHost: true,
    onJoinRequestCreated: () => {
      invalidateHostQueries();
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

  const { mutate: leaveRoomMutate } = useLeaveRoom({
    mutationConfig: {
      onError: asApiError((err) => showError(err)),
    },
  });


  if (!isPro) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-red-400">Pro required</p>
          <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
            {tActions("back")}
          </Button>
        </div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  if (isError || !roomRes?.data) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-red-400">{tErrors("roomNotFound")}</p>
          <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
            {tActions("back")}
          </Button>
        </div>
      </div>
    );
  }

  const room = roomRes.data;
  const isHost = currentUserId === room.hostUserId;

  if (!isHost) {
    router.replace("/dashboard/live-rooms");
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  const isTerminal = room.status === "ENDED";

  const handleEnd = () => {
    endRoomMutate({ roomCode });
  };

  const handleLeave = () => {
    leaveRoomMutate(
      { roomCode },
      {
        onSuccess: (response) => {
          if (response.success) {
            router.push("/dashboard/live-rooms");
          }
        },
      },
    );
  };

  const handleCopyShareLink = async () => {
    if (typeof window === "undefined") return;
    const origin = window.location.origin;
    const link = `${origin}/live-room/${room.roomCode}`;
    try {
      await navigator.clipboard.writeText(link);
      toast.success(tActions("linkCopied"));
    } catch {
      toast.error(tCommon("error"));
    }
  };

  const handleOpenRoomInNewTab = () => {
    if (typeof window === "undefined") return;
    window.open(`/live-room/${room.roomCode}`, "_blank", "noopener,noreferrer");
  };

  return (
    <div className="flex h-screen flex-col bg-neutral-950 font-sans">
      <div className="flex flex-1 flex-col overflow-hidden">
        <div className="relative flex flex-1 flex-col">
          {isTerminal ? (
            <div className="flex flex-1 items-center justify-center">
              <div className="text-center">
                <p className="text-lg font-medium text-neutral-300">Room ended</p>
                <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")} className="mt-4">
                  {tActions("back")}
                </Button>
              </div>
            </div>
          ) : currentUserId ? (
            <MediaStage
              roomCode={roomCode}
              localUserId={currentUserId}
              localDisplayName={localDisplayName}
              enabled
              onLeave={handleLeave}
            />
          ) : null}

          <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
              <span className="text-sm font-medium text-white">{room.title}</span>
              <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
            </div>
            {!isTerminal && (
              <>
                <Button size="sm" variant="ghost" onClick={handleCopyShareLink} className="h-8 rounded-full bg-black/60 px-3 text-xs text-white backdrop-blur-sm hover:bg-black/80">
                  <Link2 className="mr-1.5 size-3.5" />
                  Copy link
                </Button>
                <Button size="sm" variant="ghost" onClick={handleOpenRoomInNewTab} className="h-8 rounded-full bg-black/60 px-3 text-xs text-white backdrop-blur-sm hover:bg-black/80">
                  <ExternalLink className="mr-1.5 size-3.5" />
                  Open
                </Button>
              </>
            )}
          </div>


        </div>

        {sidebarTab && (
          <aside className="flex w-80 shrink-0 flex-col border-l border-neutral-800 bg-neutral-900">
            <div className="flex items-center justify-between border-b border-neutral-800 px-4 py-3">
              <span className="text-sm font-medium text-white">
                {sidebarTab === "requests" ? "Join Requests" : "Participants"}
              </span>
              <Button size="sm" variant="ghost" onClick={() => setSidebarTab(null)} className="text-neutral-400 hover:text-white">
                ✕
              </Button>
            </div>
            {sidebarTab === "requests" && (
              <div className="flex-1 overflow-y-auto p-3">
                <JoinRequestQueuePanel roomCode={room.roomCode} />
              </div>
            )}
            {sidebarTab === "participants" && (
              <div className="flex-1 overflow-y-auto p-3">
                <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
              </div>
            )}
          </aside>
        )}

        {!sidebarTab && !isTerminal && (
          <div className="absolute bottom-24 right-6 z-10 flex shrink-0 flex-col items-center gap-3">
            {pendingCount > 0 && (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setSidebarTab("requests")}
                className="relative flex-col gap-1 rounded-full bg-black/60 p-3 text-white backdrop-blur-sm hover:bg-black/80"
              >
                <span className="text-xs font-medium">Requests</span>
                <span className="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full bg-red-500 text-[10px] font-medium text-white">
                  {pendingCount > 9 ? "9+" : pendingCount}
                </span>
              </Button>
            )}
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setSidebarTab("participants")}
              className="flex-col gap-1 rounded-full bg-black/60 p-3 text-white backdrop-blur-sm hover:bg-black/80"
            >
              <span className="text-xs font-medium">People</span>
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
