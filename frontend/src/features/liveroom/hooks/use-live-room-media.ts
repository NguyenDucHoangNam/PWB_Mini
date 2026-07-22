"use client";

import { useCallback, useEffect, useMemo, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  applyTrackMutedFlag,
  useLiveRoomMediaStore,
  type RemotePeerStream,
} from "../stores/use-live-room-media-store";
import { useMediaDevices, MediaPermissionException } from "./use-media-devices";
import { usePeerSignaling } from "./use-peer-signaling";
import {
  liveRoomParticipantsKey,
  useUpdateMyMedia,
} from "../api/participants";
import { liveRoomKey } from "../api/rooms";
import { WebRTCPeerManager } from "../lib/webrtc-peer-manager";
import { disconnectStompClient } from "../api/ws";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";

export interface UseLiveRoomMediaParams {
  roomCode: string;
  localUserId: string;
  localDisplayName: string;
  enabled: boolean;
}

export function useLiveRoomMedia({
  roomCode,
  localUserId,
  localDisplayName,
  enabled,
}: UseLiveRoomMediaParams) {
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tMedia = useTranslations("liveroom.media");
  const queryClient = useQueryClient();

  const setLocalStream = useLiveRoomMediaStore((state) => state.setLocalStream);
  const setMicMuted = useLiveRoomMediaStore((state) => state.setMicMuted);
  const setCameraOff = useLiveRoomMediaStore((state) => state.setCameraOff);
  const setErrorMessage = useLiveRoomMediaStore((state) => state.setErrorMessage);
  const reset = useLiveRoomMediaStore((state) => state.reset);
  const upsertRemotePeer = useLiveRoomMediaStore((state) => state.upsertRemotePeer);
  const removeRemotePeer = useLiveRoomMediaStore((state) => state.removeRemotePeer);
  const localStreamState = useLiveRoomMediaStore((state) => state.localStream);

  const devices = useMediaDevices();

  const startMedia = useCallback(async () => {
    await devices.start();
  }, [devices]);

  const startMediaRef = useRef(startMedia);
  useEffect(() => {
    startMediaRef.current = startMedia;
  }, [startMedia]);

  useEffect(() => {
    if (!enabled) return;
    if (typeof window === "undefined") return;
    if (!navigator.mediaDevices?.getUserMedia) return;
    void startMediaRef.current().catch(() => {
      /* permission denied or device not found - intentional fallback */
    });
  }, [enabled]);

  const managerRef = useRef<WebRTCPeerManager | null>(null);
  const managerApiRef = useRef<{
    addPeer: (remoteUserId: string) => Promise<void>;
    queuePeerIfNeeded: (remoteUserId: string) => Promise<void>;
    removePeer: (remoteUserId: string) => void;
  } | null>(null);

  useEffect(() => {
    if (!enabled || !roomCode) return;
    const manager = new WebRTCPeerManager({
      roomCode,
      localUserId,
      localUserDisplayName: localDisplayName,
    });
    managerRef.current = manager;
    managerApiRef.current = {
      addPeer: async (remoteUserId) => {
        if (!devices.stream) {
          await manager.queuePeerIfNeeded(remoteUserId);
          return;
        }
        await manager.addPeer(devices.stream, remoteUserId);
      },
      queuePeerIfNeeded: (remoteUserId) => manager.queuePeerIfNeeded(remoteUserId),
      removePeer: (remoteUserId) => {
        manager.removePeer(remoteUserId);
      },
    };
    const offStream = manager.onRemoteStream((userId, displayName, stream) => {
      upsertRemotePeer({ userId, displayName, stream } as RemotePeerStream);
    });
    const offLeft = manager.onPeerLeft((userId) => {
      removeRemotePeer(userId);
    });
    return () => {
      offStream();
      offLeft();
      manager.close();
      if (managerRef.current === manager) {
        managerRef.current = null;
        managerApiRef.current = null;
      }
    };
  }, [enabled, roomCode, localUserId, localDisplayName, upsertRemotePeer, removeRemotePeer]);

  useEffect(() => {
    if (!devices.stream) {
      setLocalStream(null);
      return;
    }
    setLocalStream(devices.stream);
    managerRef.current?.setLocalStreamForAllPeers(devices.stream);
  }, [devices.stream, setLocalStream]);

  usePeerSignaling({
    roomCode,
    managerRef: managerApiRef,
    enabled: enabled && Boolean(devices.stream),
  });

  const updateMyMediaMutation = useUpdateMyMedia({
    mutationConfig: {
      onError: (err) => {
        toast.error(resolveLiveroomErrorMessage(err, (k) => tErrors(k as never), (k) => tCommon(k as never)));
      },
    },
  });

  const showErrorToast = useCallback(
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

  const toggleMic = useCallback(() => {
    const next = !useLiveRoomMediaStore.getState().micMuted;
    setMicMuted(next);
    applyTrackMutedFlag(devices.stream, "audio", next);
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: next,
        cameraOff: useLiveRoomMediaStore.getState().cameraOff,
      },
    });
  }, [devices.stream, roomCode, setMicMuted, updateMyMediaMutation]);

  const toggleCamera = useCallback(() => {
    const next = !useLiveRoomMediaStore.getState().cameraOff;
    setCameraOff(next);
    applyTrackMutedFlag(devices.stream, "video", next);
    if (!roomCode) return;
    updateMyMediaMutation.mutate({
      roomCode,
      body: {
        micMuted: useLiveRoomMediaStore.getState().micMuted,
        cameraOff: next,
      },
    });
  }, [devices.stream, roomCode, setCameraOff, updateMyMediaMutation]);

  const setAudioDevice = useCallback(
    async (deviceId: string) => {
      try {
        await devices.setAudio(deviceId);
      } catch (err) {
        showErrorToast(err);
      }
    },
    [devices, showErrorToast],
  );

  const setVideoDevice = useCallback(
    async (deviceId: string) => {
      try {
        await devices.setVideo(deviceId);
      } catch (err) {
        showErrorToast(err);
      }
    },
    [devices, showErrorToast],
  );

  const stopAndDisconnect = useCallback(() => {
    devices.stop();
    managerRef.current?.close();
    reset();
    if (roomCode) {
      queryClient.invalidateQueries({ queryKey: liveRoomKey(roomCode) });
      queryClient.invalidateQueries({ queryKey: liveRoomParticipantsKey(roomCode) });
    }
    disconnectStompClient();
  }, [devices, reset, queryClient, roomCode]);

  const disconnectRef = useRef(stopAndDisconnect);
  useEffect(() => {
    disconnectRef.current = stopAndDisconnect;
  }, [stopAndDisconnect]);

  useEffect(() => {
    return () => {
      disconnectRef.current?.();
    };
  }, [enabled, roomCode, queryClient, disconnectRef]);

  useEffect(() => {
    if (devices.permissionState === "denied") {
      setErrorMessage(tMedia("permissionDenied"));
    } else if (devices.errorMessage) {
      setErrorMessage(devices.errorMessage);
    } else {
      setErrorMessage(null);
    }
  }, [devices.errorMessage, devices.permissionState, setErrorMessage, tMedia]);

  const permission = useMemo(() => {
    if (devices.permissionState === "denied") {
      return new MediaPermissionException("PERMISSION_DENIED", tMedia("permissionDenied"));
    }
    return null;
  }, [devices.permissionState, tMedia]);

  return {
    stream: devices.stream,
    micMuted: useLiveRoomMediaStore((state) => state.micMuted),
    cameraOff: useLiveRoomMediaStore((state) => state.cameraOff),
    audioDevices: devices.audioDevices,
    videoDevices: devices.videoDevices,
    currentAudioId: devices.currentAudioId,
    currentVideoId: devices.currentVideoId,
    selectingDevice: devices.selectingDevice,
    permission,
    errorMessage: useLiveRoomMediaStore((state) => state.errorMessage),
    remotePeers: useLiveRoomMediaStore((state) => state.remotePeers),
    toggleMic,
    toggleCamera,
    setAudioDevice,
    setVideoDevice,
    stop: stopAndDisconnect,
    localStreamLiveRef: localStreamState,
  };
}
