"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { MediaStage } from "./media-stage";
import { LiveRoomHeader } from "./live-room-header";
import {
  ImmersiveBottomBar,
  type ImmersivePanelTab,
} from "./immersive-bottom-bar";
import { ImmersiveRightPanel } from "./immersive-right-panel";
import { HostLeaveConfirmDialog } from "./host-leave-confirm-dialog";
import { liveRoomKey } from "../api/rooms";
import { useLeaveRoom } from "../api/participants";
import { useListJoinRequests } from "../api/join-requests";
import { liveRoomParticipantsKey } from "../api/participants";
import { useLiveRoomRealtime } from "../hooks/use-live-room-realtime";
import { useMediaSessionLifecycle } from "../hooks/use-media-session-lifecycle";
import { useImmersiveMediaControls } from "../hooks/use-immersive-media-controls";
import { asApiError } from "@/lib/api-client";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoom } from "../types";

interface ImmersiveMeetingRoomProps {
  roomCode: string;
  room: LiveRoom;
  localUserId: string;
  localDisplayName: string;
}

export function ImmersiveMeetingRoom({
  roomCode,
  room,
  localUserId,
  localDisplayName,
}: ImmersiveMeetingRoomProps) {
  useMediaSessionLifecycle();
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tActions = useTranslations("liveroom.actions");
  const queryClient = useQueryClient();
  const router = useRouter();

  const [panelTab, setPanelTab] = useState<ImmersivePanelTab>(null);
  const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);

  const mediaControls = useImmersiveMediaControls({ roomCode, localUserId });

  const { data: pendingRes } = useListJoinRequests({ roomCode, status: "PENDING" });
  const pendingCount =
    pendingRes?.success && pendingRes.data ? pendingRes.data.length : 0;

  const invalidateQueries = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
  }, [queryClient, roomCode]);

  useLiveRoomRealtime({
    roomCode,
    isHost: true,
    onJoinRequestCreated: invalidateQueries,
    onParticipantChanged: (event) => {
      invalidateQueries();
      if (event.type === "ROOM_ENDED") {
        router.replace("/dashboard/live-rooms");
      }
    },
  });

  const showError = useCallback(
    (err: unknown) => {
      toast.error(
        resolveLiveroomErrorMessage(
          err,
          (k) => tErrors(k as never),
          (k) => tCommon(k as never),
        ),
      );
    },
    [tErrors, tCommon],
  );

  const { mutate: leaveRoomMutate } = useLeaveRoom({
    mutationConfig: { onError: asApiError(showError) },
  });

  const handleLeave = useCallback(() => {
    leaveRoomMutate(
      { roomCode },
      {
        onSuccess: (response: { success: boolean }) => {
          if (response.success) {
            router.replace("/dashboard/live-rooms");
          }
        },
      },
    );
  }, [leaveRoomMutate, roomCode, router]);

  const requestLeave = useCallback(() => {
    setLeaveDialogOpen(true);
  }, []);

  const confirmLeave = useCallback(() => {
    handleLeave();
  }, [handleLeave]);

  const handleToggleMic = useCallback(() => {
    mediaControls.toggleMic();
  }, [mediaControls]);
  const handleToggleCamera = useCallback(() => {
    mediaControls.toggleCamera();
  }, [mediaControls]);

  const handleSelectTab = useCallback((tab: ImmersivePanelTab) => {
    setPanelTab(tab);
  }, []);

  const handleClosePanel = useCallback(() => {
    setPanelTab(null);
  }, []);

  const renderContent = useMemo(() => {
    if (room.status === "ENDED") {
      return (
        <div className="flex h-full items-center justify-center text-center text-neutral-300">
          <div>
            <p className="text-lg font-medium">{tActions("notActive")}</p>
          </div>
        </div>
      );
    }
    return (
      <MediaStage
        roomCode={roomCode}
        localUserId={localUserId}
        localDisplayName={localDisplayName}
        enabled
        showControls={false}
      />
    );
  }, [room.status, roomCode, localUserId, localDisplayName, tActions]);

  return (
    <div className="fixed inset-0 flex flex-col bg-neutral-950 text-white">
      <LiveRoomHeader
        title={room.title}
        roomCode={room.roomCode}
        status={room.status}
        mode={room.mode}
        onClose={requestLeave}
        variant="immersive"
      />

      <main className="relative flex-1 overflow-hidden">
        {renderContent}
        <ImmersiveRightPanel
          open={panelTab !== null}
          roomCode={roomCode}
          hostUserId={room.hostUserId}
          onClose={handleClosePanel}
        />
      </main>

      {room.status !== "ENDED" ? (
        <ImmersiveBottomBar
          micMuted={mediaControls.micMuted}
          cameraOff={mediaControls.cameraOff}
          pendingRequestCount={pendingCount}
          participantCount={room.currentParticipantCount}
          activeTab={panelTab}
          roomCode={roomCode}
          localUserId={localUserId}
          onToggleMic={handleToggleMic}
          onToggleCamera={handleToggleCamera}
          onSelectTab={handleSelectTab}
          onLeave={requestLeave}
        />
      ) : null}

      <HostLeaveConfirmDialog
        open={leaveDialogOpen}
        onOpenChange={setLeaveDialogOpen}
        onConfirm={confirmLeave}
      />
    </div>
  );
}
