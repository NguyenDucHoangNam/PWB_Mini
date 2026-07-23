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
import { useMediaSessionStore } from "../stores/use-media-session-store";
import { useMediaDevices, MediaPermissionException } from "./use-media-devices";
import { usePeerSignaling } from "./use-peer-signaling";
import {
  liveRoomParticipantsKey,
  useUpdateMyMedia,
} from "../api/participants";
import { liveRoomKey } from "../api/rooms";
import { WebRTCPeerManager } from "../lib/webrtc-peer-manager";
import { disconnectStompClient, subscribeRoomParticipants } from "../api/ws";
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
    addPeer: (remoteUserId: string, displayName?: string) => Promise<void>;
    queuePeerIfNeeded: (remoteUserId: string, displayName?: string) => Promise<void>;
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
      addPeer: async (remoteUserId, displayName) => {
        await manager.queuePeerIfNeeded(remoteUserId, displayName);
      },
      queuePeerIfNeeded: (remoteUserId, displayName) => manager.queuePeerIfNeeded(remoteUserId, displayName),
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
    enabled: enabled,
  });

  const updateMyMediaMutation = useUpdateMyMedia({
    mutationConfig: {
      onError: (err) => {
        toast.error(resolveLiveroomErrorMessage(err, (k) => tErrors(k as never), (k) => tCommon(k as never)));
      },
    },
  });

  const joinedReadyRef = useRef(false);
  const pendingMediaRef = useRef<{ micMuted: boolean; cameraOff: boolean } | null>(null);
  const pendingMutationRetryRef = useRef<{ roomCode: string; body: { micMuted: boolean; cameraOff: boolean } }[]>([]);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!roomCode) return undefined;
    if (!localUserId) return undefined;
    const subscription = subscribeRoomParticipants(roomCode, (event) => {
      if (event.type === "PARTICIPANT_JOINED" && event.userId === localUserId) {
        joinedReadyRef.current = true;
        const pending = pendingMediaRef.current;
        if (pending) {
          pendingMediaRef.current = null;
          updateMyMediaMutation.mutate({ roomCode, body: pending });
        }
        for (const pendingRetry of pendingMutationRetryRef.current) {
          updateMyMediaMutation.mutate(pendingRetry);
        }
        pendingMutationRetryRef.current = [];
        return;
      }
      if (event.type === "PARTICIPANT_LEFT" && event.userId && event.userId !== localUserId) {
        const leftUserId = event.userId;
        managerApiRef.current?.removePeer(leftUserId);
        removeRemotePeer(leftUserId);
      }
    });
    const fallback = setTimeout(() => {
      joinedReadyRef.current = true;
    }, 800);
    return () => {
      subscription.unsubscribe();
      clearTimeout(fallback);
      joinedReadyRef.current = false;
      pendingMediaRef.current = null;
      pendingMutationRetryRef.current = [];
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
    };
  }, [roomCode, localUserId, removeRemotePeer, updateMyMediaMutation]);

  useEffect(() => {
    if (!enabled || !roomCode) return undefined;
    const handleVisibilityChange = () => {
      if (document.hidden) return;
      const stream = useMediaSessionStore.getState().stream;
      if (!stream) return;
      const videoTrack = stream.getVideoTracks()[0];
      if (videoTrack && videoTrack.readyState === "paused") {
        const currentVideoId = useMediaSessionStore.getState().currentVideoId;
        if (currentVideoId) {
          devices.enableCamera().catch(() => {
            /* ignore if reacquire fails */
          });
        }
      }
      for (const pendingRetry of pendingMutationRetryRef.current) {
        updateMyMediaMutation.mutate(pendingRetry);
      }
      pendingMutationRetryRef.current = [];
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled, roomCode, devices, updateMyMediaMutation]);

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

  const applyLocalMediaState = useCallback(
    async (next: { micMuted: boolean; cameraOff: boolean }) => {
      const before = useLiveRoomMediaStore.getState();
      const micChanged = before.micMuted !== next.micMuted;
      const cameraChanged = before.cameraOff !== next.cameraOff;
      if (!micChanged && !cameraChanged) return;
      setMicMuted(next.micMuted);
      setCameraOff(next.cameraOff);
      try {
        if (micChanged) {
          const currentStream = useMediaSessionStore.getState().stream;
          applyTrackMutedFlag(currentStream, "audio", next.micMuted);
        }
        if (cameraChanged) {
          if (next.cameraOff) {
            await devices.disableCamera();
          } else {
            await devices.enableCamera();
          }
        }
      } catch (err) {
        setMicMuted(before.micMuted);
        setCameraOff(before.cameraOff);
        throw err;
      }
      const updatedStream = useMediaSessionStore.getState().stream;
      managerRef.current?.setLocalStreamForAllPeers(updatedStream);
    },
    [devices, managerRef, setMicMuted, setCameraOff],
  );

  const toggleMic = useCallback(() => {
    void (async () => {
      const before = useLiveRoomMediaStore.getState();
      const next = { micMuted: !before.micMuted, cameraOff: before.cameraOff };
      try {
        await applyLocalMediaState(next);
      } catch (err) {
        showErrorToast(err);
        return;
      }
      if (!roomCode) return;
      if (joinedReadyRef.current) {
        updateMyMediaMutation.mutate({ roomCode, body: next });
      } else {
        pendingMediaRef.current = next;
      }
    })();
  }, [applyLocalMediaState, roomCode, showErrorToast, updateMyMediaMutation]);

  const toggleCamera = useCallback(() => {
    void (async () => {
      const before = useLiveRoomMediaStore.getState();
      const next = { micMuted: before.micMuted, cameraOff: !before.cameraOff };
      try {
        await applyLocalMediaState(next);
      } catch (err) {
        showErrorToast(err);
        return;
      }
      if (!roomCode) return;
      if (joinedReadyRef.current) {
        updateMyMediaMutation.mutate({ roomCode, body: next });
      } else {
        pendingMediaRef.current = next;
      }
    })();
  }, [applyLocalMediaState, roomCode, showErrorToast, updateMyMediaMutation]);

  const syncFromServer = useCallback(
    (next: { micMuted: boolean; cameraOff: boolean }) => {
      void applyLocalMediaState(next).catch((err) => showErrorToast(err));
    },
    [applyLocalMediaState, showErrorToast],
  );

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
    syncFromServer,
  };
}
