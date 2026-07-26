"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { MediaStage, type MediaStageControls } from "./media-stage";
import { LiveRoomHeader } from "./live-room-header";
import {
  ImmersiveBottomBar,
  type ImmersivePanelTab,
} from "./immersive-bottom-bar";
import { ImmersiveRightPanel } from "./immersive-right-panel";
import { LeaveConfirmDialog } from "./leave-confirm-dialog";
import { HostLeaveDialog } from "./host-leave-dialog";
import { SharedPlaybackBar } from "./shared-playback-bar";
import { SongPickerDialog } from "./song-picker-dialog";
import { liveRoomKey, useEndRoom } from "../api/rooms";
import { useLeaveRoom } from "../api/participants";
import { useListJoinRequests } from "../api/join-requests";
import { liveRoomParticipantsKey } from "../api/participants";
import { useLiveRoomRealtime } from "../hooks/use-live-room-realtime";
import { useSharedPlayback } from "../hooks/use-shared-playback";
import { useMediaSessionLifecycle, leaveMediaSession } from "../hooks/use-media-session-lifecycle";
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
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tActions = useTranslations("liveroom.actions");
  const queryClient = useQueryClient();
  const router = useRouter();

  const isHost = localUserId === room.hostUserId;

  const handleLeaveInitiated = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
  }, [queryClient, roomCode]);

  useMediaSessionLifecycle({
    roomCode,
    isHost,
    onLeaveInitiated: handleLeaveInitiated,
  });

  const [panelTab, setPanelTab] = useState<ImmersivePanelTab>(null);
  const [participantLeaveOpen, setParticipantLeaveOpen] = useState(false);
  const [hostLeaveOpen, setHostLeaveOpen] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [mediaControls, setMediaControls] = useState<MediaStageControls>({
    toggleMic: () => {},
    toggleCamera: () => {},
    micMuted: true,
    cameraOff: true,
  });

  const handleMediaReady = useCallback((controls: MediaStageControls) => {
    setMediaControls(controls);
  }, []);

  const playback = useSharedPlayback({ roomCode, enabled: Boolean(roomCode) });
  const hasSelectedSong = Boolean(playback.song?.songId);

  const { data: pendingRes } = useListJoinRequests({
    roomCode,
    status: "PENDING",
    queryConfig: { enabled: Boolean(roomCode) && isHost },
  });
  const pendingCount =
    pendingRes?.success && pendingRes.data ? pendingRes.data.length : 0;

  const invalidateQueries = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
    queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
  }, [queryClient, roomCode]);

  useLiveRoomRealtime({
    roomCode,
    isHost,
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

  const { mutate: leaveMutate, isPending: isLeaving } = useLeaveRoom({
    mutationConfig: { onError: asApiError(showError) },
  });

  const { mutate: endMutate, isPending: isEnding } = useEndRoom({
    mutationConfig: {
      onError: asApiError(showError),
      onSuccess: (response) => {
        if (response.success) {
          router.replace("/dashboard/live-rooms");
        }
      },
    },
  });

  const handleEndRoom = useCallback(() => {
    endMutate({ roomCode });
  }, [endMutate, roomCode]);

  const handleLeave = useCallback(() => {
    leaveMediaSession(roomCode, isHost);
    handleLeaveInitiated();
    leaveMutate(
      { roomCode },
      {
        onSuccess: (response: { success: boolean }) => {
          if (response.success) {
            router.replace("/dashboard/live-rooms");
          }
        },
      },
    );
  }, [handleLeaveInitiated, isHost, leaveMutate, roomCode, router]);

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

  const handleOpenPicker = useCallback(() => {
    setPickerOpen(true);
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
        onMediaReady={handleMediaReady}
      />
    );
  }, [room.status, roomCode, localUserId, localDisplayName, tActions, handleMediaReady]);

  return (
    <div className="fixed inset-0 flex flex-col bg-neutral-950 text-white">
      <LiveRoomHeader
        title={room.title}
        roomCode={room.roomCode}
        status={room.status}
        mode={room.mode}
        variant="immersive"
      />

      <main className="relative flex-1 overflow-hidden">
        {renderContent}
        <div className="pointer-events-none absolute inset-x-0 bottom-32 z-40 flex justify-center px-6">
          <SharedPlaybackBar
            roomCode={roomCode}
            isHost={isHost}
            onChooseSong={isHost ? handleOpenPicker : undefined}
          />
        </div>
        <ImmersiveRightPanel
          open={panelTab !== null}
          roomCode={roomCode}
          hostUserId={room.hostUserId}
          isHost={isHost}
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
          isHost={isHost}
          hasSelectedSong={hasSelectedSong}
          onToggleMic={handleToggleMic}
          onToggleCamera={handleToggleCamera}
          onSelectTab={handleSelectTab}
          onLeaveAsHost={() => setHostLeaveOpen(true)}
          onLeave={() => setParticipantLeaveOpen(true)}
        />
      ) : null}

      <LeaveConfirmDialog
        open={participantLeaveOpen}
        onOpenChange={setParticipantLeaveOpen}
        onConfirm={handleLeave}
        isLeaving={isLeaving}
      />

      <HostLeaveDialog
        open={hostLeaveOpen}
        onOpenChange={setHostLeaveOpen}
        onLeaveOnly={handleLeave}
        onEndRoom={handleEndRoom}
        isLeaving={isLeaving}
        isEnding={isEnding}
      />

      {isHost ? (
        <SongPickerDialog
          open={pickerOpen}
          roomCode={roomCode}
          onOpenChange={setPickerOpen}
        />
      ) : null}
    </div>
  );
}
