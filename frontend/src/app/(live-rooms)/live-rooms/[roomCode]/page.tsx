"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { RoomStatusBadge } from "@/features/liveroom/components/room-status-badge";
import { RoomModeBadge } from "@/features/liveroom/components/room-mode-badge";
import { AskToJoinCard } from "@/features/liveroom/components/ask-to-join-card";
import { WaitingRoomCard } from "@/features/liveroom/components/waiting-room-card";
import { RejectedCard } from "@/features/liveroom/components/rejected-card";
import { ParticipantsList } from "@/features/liveroom/components/participants-list";
import { MediaStage } from "@/features/liveroom/components/media-stage";
import {
  useLeaveRoom,
  useRoom,
  useCheckRoomExists,
  useEndRoom,
  useViewerStatus,
  joinPublicRoom,
} from "@/features/liveroom";
import { useLiveRoomRealtime } from "@/features/liveroom/hooks/use-live-room-realtime";
import { resolveLiveroomErrorMessage } from "@/features/liveroom/lib/resolve-liveroom-error-message";
import type { LiveRoomJoinRequest, LiveRoomViewerStatus } from "@/features/liveroom/types";

type GuestPhase =
  | { kind: "ASK" }
  | { kind: "WAITING"; request: LiveRoomJoinRequest }
  | { kind: "REJECTED"; request: LiveRoomJoinRequest; reason: string }
  | { kind: "IN_ROOM" };

function buildPendingRequestFromStatus(
  status: LiveRoomViewerStatus,
  viewerUserId: string | null,
): LiveRoomJoinRequest {
  return {
    id: status.pendingRequestId ?? "",
    roomCode: status.roomCode,
    userId: viewerUserId ?? "",
    displayName: "",
    message: null,
    status: status.pendingStatus ?? "PENDING",
    decisionReason: null,
    decidedByUserId: null,
    decidedAt: null,
    createdAt: status.createdAt,
  };
}

export default function ListenerLiveRoomPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";

  const tCard = useTranslations("liveroom.card");
  const tActions = useTranslations("liveroom.actions");
  const tErrors = useTranslations("liveroom.errors");
  const tExists = useTranslations("liveroom.existsCheck");
  const tNav = useTranslations("liveroom.nav");
  const tParticipant = useTranslations("liveroom.participant");
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

  const [userPhase, setUserPhase] = useState<GuestPhase>({ kind: "ASK" });
  const [autoJoining, setAutoJoining] = useState(false);

  const serverStatus = viewerStatusRes?.data;
  const isHost = !authBootstrapping && serverStatus?.host === true;
  const isParticipant = !isHost && serverStatus?.participant === true;
  const hasPending = !isHost && !isParticipant && serverStatus?.pendingRequest === true;
  const roomMode = serverStatus?.roomMode ?? "PUBLIC";

  const needsAutoJoin = !isHost && !isParticipant && roomMode === "PUBLIC" && !hasPending;

  const phase: GuestPhase =
    isHost || isParticipant
      ? { kind: "IN_ROOM" }
      : autoJoining
        ? { kind: "ASK" }
        : roomMode === "PUBLIC"
          ? { kind: "IN_ROOM" }
          : hasPending && serverStatus?.pendingRequestId
            ? { kind: "WAITING", request: buildPendingRequestFromStatus(serverStatus, currentUserId) }
            : userPhase;

  const { mutate: leaveRoom, isPending: isLeaving } = useLeaveRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          setUserPhase({ kind: "ASK" });
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: endRoomFromGuest, isPending: isEnding } = useEndRoom({
    mutationConfig: {
      onSuccess: () => {
        if (typeof window !== "undefined") {
          window.close();
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
      setUserPhase((current) => {
        if (current.kind === "WAITING" && current.request.id === event.requestId) {
          if (event.status === "APPROVED") {
            toast.success(tActions("admitted"));
          } else if (event.status === "REJECTED") {
            toast.error(tActions("rejected"));
          }
        }
        return current;
      });
      void refetchViewerStatus();
    },
    onParticipantChanged: () => {
      void refetchViewerStatus();
    },
  });

  useEffect(() => {
    if (!authBootstrapping && isHost) {
      router.replace(`/dashboard/live-rooms/${roomCode}`);
    }
  }, [authBootstrapping, isHost, roomCode, router]);

  const joinedRef = useRef(false);
  useEffect(() => {
    if (!needsAutoJoin) return;
    if (joinedRef.current) return;
    joinedRef.current = true;
    setAutoJoining(true);
    joinPublicRoom({ roomCode })
      .then(() => refetchViewerStatus())
      .finally(() => setAutoJoining(false));
  }, [needsAutoJoin, roomCode, refetchViewerStatus]);

  const room = roomRes?.data ?? null;
  const isActive = room?.status === "ACTIVE";
  const localDisplayName = useAuthStore.getState().user?.username ?? "Guest";

  const handleLeave = useCallback(() => {
    if (!room) return;
    leaveRoom({ roomCode: room.roomCode });
  }, [leaveRoom, room]);

  const handleEndRoom = useCallback(() => {
    if (!room) return;
    endRoomFromGuest({ roomCode: room.roomCode });
  }, [endRoomFromGuest, room]);

  if (authBootstrapping || isHost) {
    return (
      <div className="flex h-screen items-center justify-center bg-neutral-950">
        <Spinner size="md" />
      </div>
    );
  }

  if (existsLoading || roomLoading || viewerStatusLoading) {
    return (
      <div className="flex h-screen items-center justify-center gap-3 bg-neutral-950 text-sm text-neutral-500">
        <Spinner size="md" />
        <span>{tExists("checking")}</span>
      </div>
    );
  }

  if (existsError || !existsRes?.data?.exists) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-3 bg-neutral-950 text-center">
        <p className="text-sm text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  if (roomError || !room) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-3 bg-neutral-950 text-center">
        <p className="text-sm text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  if (!isActive) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-6 bg-neutral-950">
        <div className="text-center">
          <div className="mb-2 flex flex-wrap items-center justify-center gap-2">
            <h1 className="text-2xl font-bold text-white">{room.title}</h1>
            <RoomStatusBadge status={room.status} />
            <RoomModeBadge mode={room.mode} />
          </div>
          <p className="text-sm text-neutral-400">{tExists("existsInactive")}</p>
        </div>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col bg-neutral-950 font-sans">
      <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
        <div className="flex flex-wrap items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
          <h1 className="text-sm font-medium text-white">{room.title}</h1>
          <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
          <RoomStatusBadge status={room.status} />
          <RoomModeBadge mode={room.mode} />
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

      <div className="flex flex-1 items-center justify-center px-4">
        <div className="grid w-full max-w-4xl gap-6 lg:grid-cols-2">
          <div className="flex flex-col gap-3">
            {phase.kind === "ASK" && (
              <AskToJoinCard
                roomCode={room.roomCode}
                isActive={isActive}
                onSent={(request) => setUserPhase({ kind: "WAITING", request })}
              />
            )}

            {phase.kind === "WAITING" && (
              <WaitingRoomCard
                roomCode={room.roomCode}
                request={phase.request}
                onApproved={() => setUserPhase({ kind: "IN_ROOM" })}
                onRejected={(reason) => {
                  setUserPhase({
                    kind: "REJECTED",
                    request: phase.request,
                    reason,
                  });
                }}
                onCancelled={() => setUserPhase({ kind: "ASK" })}
              />
            )}

            {phase.kind === "REJECTED" && (
              <RejectedCard
                reason={phase.reason}
                onAskAgain={() => setUserPhase({ kind: "ASK" })}
                onBack={() => setUserPhase({ kind: "ASK" })}
              />
            )}

            {phase.kind === "IN_ROOM" && currentUserId && (
              <InRoomStage
                roomCode={room.roomCode}
                localUserId={currentUserId}
                localDisplayName={localDisplayName}
                onLeave={handleLeave}
                onEndRoom={handleEndRoom}
                isLeaving={isLeaving || isEnding}
                isHost={false}
                tParticipantLeave={tParticipant("leaveRoom")}
                tParticipantEnding={
                  isLeaving || isEnding
                    ? tParticipant("leaving")
                    : null
                }
              />
            )}
          </div>

          <div className="rounded-xl border border-neutral-800 bg-black/40 p-5">
            <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
          </div>
        </div>
      </div>
    </div>
  );
}

interface InRoomStageProps {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  onLeave: () => void;
  onEndRoom: () => void;
  isLeaving: boolean;
  isHost: boolean;
  tParticipantLeave: string;
  tParticipantEnding: string | null;
}

function InRoomStage({
  roomCode,
  localUserId,
  localDisplayName,
  onLeave,
  isLeaving,
  tParticipantLeave,
  tParticipantEnding,
}: InRoomStageProps) {
  return (
    <div className="flex flex-col gap-4 rounded-xl border border-neutral-800 bg-black/40 p-5">
      <div className="flex items-center gap-2 text-sm text-green-400">
        <span className="inline-flex size-2 rounded-full bg-green-500 animate-pulse" />
        <span className="font-semibold">In Room</span>
      </div>
      <MediaStage
        roomCode={roomCode}
        localUserId={localUserId}
        localDisplayName={localDisplayName}
        enabled
        onLeave={onLeave}
      />
      <Button
        variant="destructive"
        onClick={onLeave}
        disabled={isLeaving}
        className="self-start"
      >
        {isLeaving ? (
          <span className="flex items-center gap-2">
            <Spinner size="sm" />
            {tParticipantEnding ?? tParticipantLeave}
          </span>
        ) : (
          <span className="flex items-center gap-2">
            <LogOut className="size-4" />
            {tParticipantLeave}
          </span>
        )}
      </Button>
    </div>
  );
}
