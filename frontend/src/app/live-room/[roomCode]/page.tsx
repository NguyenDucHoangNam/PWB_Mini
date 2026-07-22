"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { MediaStage } from "@/features/liveroom/components/media-stage";
import { AskToJoinCard } from "@/features/liveroom/components/ask-to-join-card";
import { WaitingRoomCard } from "@/features/liveroom/components/waiting-room-card";
import { RejectedCard } from "@/features/liveroom/components/rejected-card";
import {
  useLeaveRoom,
  useRoom,
  useCheckRoomExists,
  useViewerStatus,
} from "@/features/liveroom";
import { useLiveRoomRealtime } from "@/features/liveroom/hooks/use-live-room-realtime";
import { asApiError } from "@/lib/api-client";
import { resolveLiveroomErrorMessage } from "@/features/liveroom/lib/resolve-liveroom-error-message";
import type { LiveRoomJoinRequest } from "@/features/liveroom/types";

type GuestPhase =
  | { kind: "ASK" }
  | { kind: "WAITING"; request: LiveRoomJoinRequest }
  | { kind: "REJECTED"; reason: string }
  | { kind: "IN_ROOM" };

export default function ListenerLiveRoomPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";

  const tActions = useTranslations("liveroom.actions");
  const tErrors = useTranslations("liveroom.errors");
  const tExists = useTranslations("liveroom.existsCheck");
  const tCommon = useTranslations("common");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);
  const authBootstrapping = useAuthStore((state) => state.bootstrapping);

  const { data: existsRes, isLoading: existsLoading, isError: existsError } =
    useCheckRoomExists({ roomCode });
  const { data: roomRes, isLoading: roomLoading, isError: roomError } = useRoom({
    roomCode,
    queryConfig: { enabled: Boolean(existsRes?.data?.exists) },
  });
  const {
    data: viewerStatusRes,
    isLoading: viewerStatusLoading,
    refetch: refetchViewerStatus,
  } = useViewerStatus({
    roomCode,
    queryConfig: { enabled: Boolean(existsRes?.data?.exists) },
  });

  const [phase, setPhase] = useState<GuestPhase>({ kind: "ASK" });
  const [pendingRequest, setPendingRequest] = useState<LiveRoomJoinRequest | null>(null);

  const serverStatus = viewerStatusRes?.data;
  const isHost = !authBootstrapping && serverStatus?.host === true;
  const isParticipant = !isHost && serverStatus?.participant === true;

  const localDisplayName = useAuthStore.getState().user?.username ?? "Guest";

  const { mutate: leaveRoom } = useLeaveRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          setPhase({ kind: "ASK" });
          setPendingRequest(null);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  useLiveRoomRealtime({
    roomCode,
    isHost: false,
    onJoinRequestDecided: (event) => {
      if (phase.kind === "WAITING" && pendingRequest && event.requestId === pendingRequest.id) {
        if (event.status === "APPROVED") {
          toast.success(tActions("admitted"));
          setPhase({ kind: "IN_ROOM" });
        } else if (event.status === "REJECTED") {
          toast.error(tActions("rejected"));
          setPhase({ kind: "REJECTED", reason: event.decisionReason ?? "" });
        }
      }
      void refetchViewerStatus();
    },
    onParticipantChanged: (event) => {
      if (event.type === "ROOM_ENDED") {
        toast.info(tActions("notActive"));
        setPhase({ kind: "ASK" });
        setPendingRequest(null);
      }
      void refetchViewerStatus();
    },
  });

  useEffect(() => {
    if (!authBootstrapping && isHost) {
      router.replace(`/dashboard/live-rooms/${roomCode}`);
    }
  }, [authBootstrapping, isHost, roomCode, router]);

  const handleLeave = useCallback(() => {
    leaveRoom({ roomCode });
  }, [leaveRoom, roomCode]);

  const handleRequestSent = useCallback((request: LiveRoomJoinRequest) => {
    setPendingRequest(request);
    setPhase({ kind: "WAITING", request });
  }, []);

  const handleApproved = useCallback(() => {
    setPhase({ kind: "IN_ROOM" });
  }, []);

  const handleRejected = useCallback((reason: string) => {
    setPhase({ kind: "REJECTED", reason });
  }, []);

  const handleCancelled = useCallback(() => {
    setPhase({ kind: "ASK" });
    setPendingRequest(null);
  }, []);

  if (authBootstrapping) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  if (isHost) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  if (existsLoading || roomLoading || viewerStatusLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  if (existsError || !existsRes?.data?.exists) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-neutral-950">
        <p className="mb-4 text-sm text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  if (roomError || !roomRes?.data) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-neutral-950">
        <p className="mb-4 text-sm text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const room = roomRes.data;
  const isActive = room.status === "ACTIVE";

  if (!isActive) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-neutral-950">
        <div className="mb-4 text-center">
          <h1 className="text-xl font-semibold text-white">{room.title}</h1>
          <p className="mt-2 text-sm text-neutral-400">{tActions("notActive")}</p>
        </div>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const isPublic = room.mode === "PUBLIC";

  return (
    <div className="flex h-screen flex-col bg-neutral-950">
      <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
        <div className="flex items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
          <span className="text-sm font-medium text-white">{room.title}</span>
          <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
        </div>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => router.push("/dashboard/live-rooms")}
          className="rounded-full bg-black/60 px-3 text-xs text-white backdrop-blur-sm hover:bg-black/80"
        >
          ✕
        </Button>
      </div>

      {phase.kind === "ASK" && (
        <div className="flex flex-1 items-center justify-center px-4">
          {isPublic ? (
            <div className="text-center">
              <h1 className="text-3xl font-bold text-white">{room.title}</h1>
              {room.description && (
                <p className="mt-2 max-w-md text-sm text-neutral-400">{room.description}</p>
              )}
              <p className="mt-1 text-xs text-neutral-500">
                {tExists("existsActive")} • {room.currentParticipantCount}/{room.maxParticipants} participants
              </p>
              <Button className="mt-6" onClick={() => setPhase({ kind: "IN_ROOM" })}>
                {tActions("joinRoom")}
              </Button>
            </div>
          ) : (
            <AskToJoinCard
              roomCode={roomCode}
              isActive={isActive}
              onSent={handleRequestSent}
            />
          )}
        </div>
      )}

      {phase.kind === "WAITING" && pendingRequest && (
        <div className="flex flex-1 items-center justify-center px-4">
          <WaitingRoomCard
            roomCode={roomCode}
            request={pendingRequest}
            onApproved={handleApproved}
            onRejected={handleRejected}
            onCancelled={handleCancelled}
          />
        </div>
      )}

      {phase.kind === "REJECTED" && (
        <div className="flex flex-1 items-center justify-center px-4">
          <RejectedCard
            reason={phase.reason}
            onAskAgain={() => {
              setPhase({ kind: "ASK" });
              setPendingRequest(null);
            }}
            onBack={() => router.push("/dashboard/live-rooms")}
          />
        </div>
      )}

      {phase.kind === "IN_ROOM" && currentUserId && (
        <MediaStage
          roomCode={roomCode}
          localUserId={currentUserId}
          localDisplayName={localDisplayName}
          enabled
          onLeave={handleLeave}
        />
      )}
    </div>
  );
}
