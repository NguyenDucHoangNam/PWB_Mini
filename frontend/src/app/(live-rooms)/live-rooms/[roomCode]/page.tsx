"use client";

import { useState } from "react";
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
import { MediaControls } from "@/features/liveroom/components/media-controls";
import { useLiveRoomMedia } from "@/features/liveroom/hooks/use-live-room-media";
import {
  useLeaveRoom,
  useRoom,
  useCheckRoomExists,
  useEndRoom,
  useViewerStatus,
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

  const serverStatus = viewerStatusRes?.data;
  const isHost = !authBootstrapping && serverStatus?.host === true;
  const isParticipant = !isHost && serverStatus?.participant === true;
  const hasPending = !isHost && !isParticipant && serverStatus?.pendingRequest === true;

  const [userPhase, setUserPhase] = useState<GuestPhase>({ kind: "ASK" });
  const phase: GuestPhase = isHost || isParticipant
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

  if (existsLoading || roomLoading || viewerStatusLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
        <span>{tExists("checking")}</span>
      </div>
    );
  }

  if (existsError || !existsRes?.data?.exists) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tExists("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  if (roomError || !roomRes?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tExists("notFound")}</p>
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
      <div className="flex flex-col gap-6 font-sans">
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
              {room.title}
            </h1>
            <RoomStatusBadge status={room.status} />
            <RoomModeBadge mode={room.mode} />
          </div>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {tExists("existsInactive")}
          </p>
        </div>
        <Button variant="outline" className="self-start" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const handleLeave = () => {
    if (isHost) {
      const confirmed = typeof window !== "undefined"
        ? window.confirm(tActions("endRoomConfirm"))
        : false;
      if (!confirmed) return;
      endRoomFromGuest({ roomCode: room.roomCode });
      return;
    }
    leaveRoom({ roomCode: room.roomCode });
  };

  const localDisplayName = useAuthStore.getState().user?.username ?? "Guest";

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2">
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
        {room.description && (
          <p className="text-sm text-neutral-600 dark:text-neutral-300 max-w-prose">
            {room.description}
          </p>
        )}
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {tNav("listenerSubtitle")}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
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
              isLeaving={isLeaving || isEnding}
              isHost={isHost}
              tParticipantLeave={isHost ? tParticipant("endRoom") : tParticipant("leaveRoom")}
              tParticipantEnding={
                isLeaving || isEnding
                  ? isHost
                    ? tParticipant("ending")
                    : tParticipant("leaving")
                  : null
              }
            />
          )}
        </div>

        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
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
  isHost,
  tParticipantLeave,
  tParticipantEnding,
}: InRoomStageProps) {
  const tParticipant = useTranslations("liveroom.participant");
  const media = useLiveRoomMedia({
    roomCode,
    localUserId,
    localDisplayName,
    enabled: true,
  });

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
      <div className="flex items-center justify-between text-sm text-green-700 dark:text-green-300">
        <span className="flex items-center gap-2 font-semibold">
          <span className="inline-flex size-2 rounded-full bg-green-500 animate-pulse" />
          {tParticipant("joinedHeader")}
        </span>
      </div>
      <MediaStage
        roomCode={roomCode}
        localUserId={localUserId}
        localDisplayName={localDisplayName}
        enabled
      />
      <MediaControls
        micMuted={media.micMuted}
        cameraOff={media.cameraOff}
        selectingDevice={media.selectingDevice}
        audioDevices={media.audioDevices}
        videoDevices={media.videoDevices}
        currentAudioId={media.currentAudioId}
        currentVideoId={media.currentVideoId}
        onToggleMic={media.toggleMic}
        onToggleCamera={media.toggleCamera}
        onSelectAudio={media.setAudioDevice}
        onSelectVideo={media.setVideoDevice}
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
      {isHost && (
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {tParticipant("hostInGuestTabHint")}
        </p>
      )}
    </div>
  );
}
