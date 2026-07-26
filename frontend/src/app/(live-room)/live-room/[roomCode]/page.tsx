"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { ImmersiveMeetingRoom } from "@/features/liveroom/components/immersive-meeting-room";
import { PreJoinScreen } from "@/features/liveroom/components/pre-join-screen";
import { WaitingRoomCard } from "@/features/liveroom/components/waiting-room-card";
import { RejectedCard } from "@/features/liveroom/components/rejected-card";
import {
  useRoom,
  useCheckRoomExists,
  useViewerStatus,
} from "@/features/liveroom";
import { buildPendingRequestFromStatus } from "@/features/liveroom/lib/build-pending-request";
import type { LiveRoomJoinRequest } from "@/features/liveroom/types";

type GuestPhase =
  | { kind: "ASK" }
  | { kind: "WAITING"; request: LiveRoomJoinRequest }
  | { kind: "REJECTED"; reason: string }
  | { kind: "IN_ROOM" };

export default function UnifiedLiveRoomPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";
  const queryClient = useQueryClient();

  const tActions = useTranslations("liveroom.actions");
  const tExists = useTranslations("liveroom.existsCheck");
  const tCommon = useTranslations("common");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);
  const localDisplayName = useAuthStore.getState().user?.username ?? "Guest";
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
  } = useViewerStatus({
    roomCode,
    queryConfig: { enabled: Boolean(existsRes?.data?.exists) },
  });

  const [phase, setPhase] = useState<GuestPhase | null>(null);
  const [pendingRequest, setPendingRequest] = useState<LiveRoomJoinRequest | null>(null);
  const [hostPreJoinDone, setHostPreJoinDone] = useState(false);

  const serverStatus = viewerStatusRes?.data;
  const isHost = !authBootstrapping && serverStatus?.host === true;

  useEffect(() => {
    if (phase !== null) return;
    if (!serverStatus) return;

    if (serverStatus.host) {
      return;
    }

    if (serverStatus.participant) {
      setPhase({ kind: "IN_ROOM" });
      return;
    }

    if (serverStatus.pendingRequest && serverStatus.pendingStatus === "PENDING") {
      const req = buildPendingRequestFromStatus(serverStatus, currentUserId);
      setPendingRequest(req);
      setPhase({ kind: "WAITING", request: req });
      return;
    }

    setPhase({ kind: "ASK" });
  }, [serverStatus, phase, currentUserId]);

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

  const handleJoinedPublic = useCallback(() => {
    setPhase({ kind: "IN_ROOM" });
  }, []);

  const handleHostJoined = useCallback(() => {
    setHostPreJoinDone(true);
  }, []);

  if (authBootstrapping) {
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
  const isTerminal = room.status === "ENDED";

  if (isTerminal) {
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

  const resolvedPhase = phase ?? { kind: "ASK" as const };

  if (isHost && !hostPreJoinDone) {
    return (
      <PreJoinScreen
        room={room}
        mode="HOST"
        onJoinedPublic={handleJoinedPublic}
        onJoinedAsHost={handleHostJoined}
        onRequestSent={handleRequestSent}
      />
    );
  }

  if ((isHost || phase?.kind === "IN_ROOM") && currentUserId) {
    return (
      <ImmersiveMeetingRoom
        roomCode={roomCode}
        room={room}
        localUserId={currentUserId}
        localDisplayName={localDisplayName}
      />
    );
  }

  if (resolvedPhase.kind === "ASK") {
    return (
      <PreJoinScreen
        room={room}
        mode={room.mode === "PUBLIC" ? "PUBLIC" : "PRIVATE"}
        onJoinedPublic={handleJoinedPublic}
        onRequestSent={handleRequestSent}
      />
    );
  }

  if (resolvedPhase.kind === "WAITING" && pendingRequest) {
    return (
      <div className="flex h-screen flex-col bg-neutral-950">
        <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
            <span className="text-sm font-medium text-white">{room.title}</span>
            <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
          </div>
        </div>
        <div className="flex flex-1 items-center justify-center px-4">
          <WaitingRoomCard
            roomCode={roomCode}
            request={pendingRequest}
            onApproved={handleApproved}
            onRejected={handleRejected}
            onCancelled={handleCancelled}
          />
        </div>
      </div>
    );
  }

  if (resolvedPhase.kind === "REJECTED") {
    return (
      <div className="flex h-screen flex-col bg-neutral-950">
        <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
            <span className="text-sm font-medium text-white">{room.title}</span>
            <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
          </div>
        </div>
        <div className="flex flex-1 items-center justify-center px-4">
          <RejectedCard
            reason={resolvedPhase.reason}
            onAskAgain={() => {
              setPhase({ kind: "ASK" });
              setPendingRequest(null);
            }}
            onBack={() => router.push("/dashboard/live-rooms")}
          />
        </div>
      </div>
    );
  }

  return null;
}