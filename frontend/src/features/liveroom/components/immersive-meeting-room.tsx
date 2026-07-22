"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { MediaStage } from "./media-stage";
import { ImmersiveTopBar } from "./immersive-top-bar";
import {
  ImmersiveBottomBar,
  type ImmersivePanelTab,
} from "./immersive-bottom-bar";
import { ImmersiveRightPanel } from "./immersive-right-panel";
import {
  useEndRoom,
  liveRoomKey,
} from "../api/rooms";
import { useLeaveRoom } from "../api/participants";
import { useListJoinRequests } from "../api/join-requests";
import { liveRoomParticipantsKey } from "../api/participants";
import { useLiveRoomRealtime } from "../hooks/use-live-room-realtime";
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
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tActions = useTranslations("liveroom.actions");
  const queryClient = useQueryClient();

  const [panelTab, setPanelTab] = useState<ImmersivePanelTab>(null);

  const mediaControls = useImmersiveMediaControls({ roomCode });

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
    onParticipantChanged: invalidateQueries,
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

  const { mutate: endRoomMutate } = useEndRoom({
    mutationConfig: { onError: asApiError(showError) },
  });

  const { mutate: leaveRoomMutate } = useLeaveRoom({
    mutationConfig: { onError: asApiError(showError) },
  });

  const handleEnd = useCallback(() => {
    endRoomMutate({ roomCode });
  }, [endRoomMutate, roomCode]);

  const handleLeave = useCallback(() => {
    leaveRoomMutate(
      { roomCode },
      {
        onSuccess: (response: { success: boolean }) => {
          if (response.success) {
            window.location.href = "/dashboard/live-rooms";
          }
        },
      },
    );
  }, [leaveRoomMutate, roomCode]);

  useEffect(() => {
    if (room.status === "ENDED") {
      const timer = setTimeout(() => {
        window.location.href = "/dashboard/live-rooms";
      }, 1500);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [room.status]);

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
      <ImmersiveTopBar room={room} onClose={handleLeave} />

      <main className="relative flex-1 overflow-hidden">
        {renderContent}
        <ImmersiveRightPanel
          open={panelTab !== null}
          tab={panelTab}
          roomCode={roomCode}
          hostUserId={room.hostUserId}
          localUserId={localUserId}
          localDisplayName={localDisplayName}
          onClose={handleClosePanel}
        />
      </main>

      {room.status !== "ENDED" ? (
        <ImmersiveBottomBar
          micMuted={mediaControls.micMuted}
          cameraOff={mediaControls.cameraOff}
          pendingRequestCount={pendingCount}
          participantCount={room.currentParticipantCount}
          unreadMessageCount={0}
          activeTab={panelTab}
          onToggleMic={handleToggleMic}
          onToggleCamera={handleToggleCamera}
          onSelectTab={handleSelectTab}
          onLeave={handleEnd}
        />
      ) : null}
    </div>
  );
}