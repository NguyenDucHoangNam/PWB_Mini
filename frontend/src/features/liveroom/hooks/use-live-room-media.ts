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
import { mediaSessionController } from "../lib/media-session";
import { MediaTrackController } from "../lib/media-track-controller";
import { disconnectStompClient, requestRoomState, subscribeRoomParticipants } from "../api/ws";
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

  const managerRef = useRef<WebRTCPeerManager | null>(null);
  const managerApiRef = useRef<{
    addPeer: (remoteUserId: string, displayName?: string) => Promise<void>;
    queuePeerIfNeeded: (remoteUserId: string, displayName?: string) => Promise<void>;
    removePeer: (remoteUserId: string) => void;
  } | null>(null);

  const trackControllerRef = useRef<MediaTrackController | null>(null);

  const lastSyncedMediaRef = useRef<{ micMuted: boolean; cameraOff: boolean } | null>(null);
  const joinedReadyRef = useRef(false);
  const pendingMediaRef = useRef<{ micMuted: boolean; cameraOff: boolean } | null>(null);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const updateMyMediaMutationRef = useRef<ReturnType<typeof useUpdateMyMedia> | null>(null);

  const syncMediaToServer = useCallback(
    (next: { micMuted: boolean; cameraOff: boolean }) => {
      if (!roomCode) return;
      const last = lastSyncedMediaRef.current;
      if (last && last.micMuted === next.micMuted && last.cameraOff === next.cameraOff) return;
      lastSyncedMediaRef.current = next;
      updateMyMediaMutationRef.current?.mutate({ roomCode, body: next });
    },
    [roomCode],
  );

  useEffect(() => {
    if (!enabled) return;
    if (typeof window === "undefined") return;
    if (!navigator.mediaDevices?.getUserMedia) return;
    void startMediaRef.current()
      .then(() => {
        const state = useLiveRoomMediaStore.getState();
        const stream = useMediaSessionStore.getState().stream;
        if (stream) {
          applyTrackMutedFlag(stream, "audio", state.micMuted);
          if (state.cameraOff) {
            for (const track of stream.getVideoTracks()) {
              try {
                track.stop();
              } catch {
                /* ignore */
              }
              try {
                stream.removeTrack(track);
              } catch {
                /* ignore */
              }
            }
            useMediaSessionStore.getState().setCurrentVideoId(null);
            useMediaSessionStore.getState().bumpStreamRevision();
          }
        }
        if (trackControllerRef.current) {
          trackControllerRef.current.hydrate(!state.micMuted, !state.cameraOff);
        }
        setTimeout(() => {
          if (!roomCode) return;
          const currentState = useLiveRoomMediaStore.getState();
          const last = lastSyncedMediaRef.current;
          if (last && last.micMuted === currentState.micMuted && last.cameraOff === currentState.cameraOff) return;
          lastSyncedMediaRef.current = { micMuted: currentState.micMuted, cameraOff: currentState.cameraOff };
          updateMyMediaMutationRef.current?.mutate({
            roomCode,
            body: { micMuted: currentState.micMuted, cameraOff: currentState.cameraOff },
          });
        }, 1000);
      })
      .catch((err) => {
        console.warn("[LIVEROOM-DEBUG] devices.start() failed:", err);
      });
  }, [enabled, roomCode]);

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
        const stream = useMediaSessionStore.getState().stream;
        if (stream) {
          await manager.onPeerJoined(remoteUserId, stream);
        } else {
          await manager.queuePeerIfNeeded(remoteUserId, displayName);
        }
      },
      queuePeerIfNeeded: (remoteUserId, displayName) => manager.queuePeerIfNeeded(remoteUserId, displayName),
      removePeer: (remoteUserId) => {
        manager.removePeer(remoteUserId);
      },
    };
    const existingStream = useMediaSessionStore.getState().stream;
    if (existingStream) {
      manager.setLocalStreamForAllPeers(existingStream);
    }
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
    if (!enabled || !roomCode) return;

    const initialState = useLiveRoomMediaStore.getState();
    const controller = new MediaTrackController({
      getLocalStream: () => useMediaSessionStore.getState().stream,
      acquireMic: async () => {
        await mediaSessionController.ensureAudio();
        return useMediaSessionStore.getState().stream?.getAudioTracks()[0] ?? null;
      },
      acquireCamera: async () => mediaSessionController.enableCamera(),
      releaseTrack: (track) => {
        const stream = useMediaSessionStore.getState().stream;
        if (stream) {
          try {
            stream.removeTrack(track);
          } catch {
            /* ignore */
          }
        }
        try {
          track.stop();
        } catch {
          /* ignore */
        }
      },
      getPeerManager: () => managerRef.current,
    });

    controller.hydrate(!initialState.micMuted, !initialState.cameraOff);
    trackControllerRef.current = controller;

    const unsubscribe = controller.onChange((event) => {
      if (event.type === "MIC_CHANGED") {
        setMicMuted(!event.enabled);
        const next = {
          micMuted: !event.enabled,
          cameraOff: !controller.getCameraEnabled(),
        };
        if (joinedReadyRef.current) {
          syncMediaToServer(next);
        } else {
          pendingMediaRef.current = next;
        }
      }
      if (event.type === "CAMERA_CHANGED") {
        setCameraOff(!event.enabled);
        const next = {
          micMuted: !controller.getMicEnabled(),
          cameraOff: !event.enabled,
        };
        if (joinedReadyRef.current) {
          syncMediaToServer(next);
        } else {
          pendingMediaRef.current = next;
        }
      }
    });

    return () => {
      unsubscribe();
      if (trackControllerRef.current === controller) {
        trackControllerRef.current = null;
      }
    };
  }, [enabled, roomCode, setMicMuted, setCameraOff, syncMediaToServer]);

  const streamRevision = useMediaSessionStore((s) => s.streamRevision);

  const prevStreamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    if (!devices.stream) {
      prevStreamRef.current = null;
      setLocalStream(null);
      managerRef.current?.setLocalStreamForAllPeers(null);
      return;
    }
    const wasNull = prevStreamRef.current === null;
    prevStreamRef.current = devices.stream;
    setLocalStream(devices.stream);
    managerRef.current?.setLocalStreamForAllPeers(devices.stream);

    const currentMediaState = useLiveRoomMediaStore.getState();
    applyTrackMutedFlag(devices.stream, "audio", currentMediaState.micMuted);

    if (wasNull && roomCode) {
      requestRoomState(roomCode);
    }

    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    if (wasNull && roomCode) {
      retryTimer = setTimeout(() => {
        const peers = useLiveRoomMediaStore.getState().remotePeers;
        if (peers.length === 0) {
          requestRoomState(roomCode);
        }
      }, 2000);
    }

    return () => {
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [devices.stream, streamRevision, setLocalStream, roomCode]);

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

  updateMyMediaMutationRef.current = updateMyMediaMutation;

  useEffect(() => {
    if (!roomCode) return undefined;
    if (!localUserId) return undefined;
    const subscription = subscribeRoomParticipants(roomCode, (event) => {
      if (event.type === "PARTICIPANT_JOINED" && event.userId === localUserId) {
        joinedReadyRef.current = true;
        const pending = pendingMediaRef.current;
        const currentState = useLiveRoomMediaStore.getState();
        if (pending) {
          pendingMediaRef.current = null;
          syncMediaToServer(pending);
        } else {
          syncMediaToServer({ micMuted: currentState.micMuted, cameraOff: currentState.cameraOff });
        }
        return;
      }
      if (event.type === "PARTICIPANT_LEFT" && event.userId && event.userId !== localUserId) {
        const leftUserId = event.userId;
        managerApiRef.current?.removePeer(leftUserId);
        removeRemotePeer(leftUserId);
        return;
      }
      if (event.type === "MEDIA_STATE_CHANGED" && event.userId && event.userId !== localUserId) {
        const { setRemoteMediaState } = useLiveRoomMediaStore.getState();
        setRemoteMediaState(event.userId, {
          micMuted: event.micMuted ?? true,
          cameraOff: event.cameraOff ?? true,
        });
      }
    });
    const fallback = setTimeout(() => {
      joinedReadyRef.current = true;
      const currentState = useLiveRoomMediaStore.getState();
      syncMediaToServer({ micMuted: currentState.micMuted, cameraOff: currentState.cameraOff });
    }, 800);
    return () => {
      subscription.unsubscribe();
      clearTimeout(fallback);
      joinedReadyRef.current = false;
      pendingMediaRef.current = null;
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
    };
  }, [roomCode, localUserId, removeRemotePeer, syncMediaToServer]);

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
    const controller = trackControllerRef.current;
    if (!controller) return;
    const previousEnabled = controller.getMicEnabled();
    const nextEnabled = !previousEnabled;

    setMicMuted(!nextEnabled);

    controller.setMicEnabled(nextEnabled).catch((err) => {
      setMicMuted(previousEnabled);
      showErrorToast(err);
    });
  }, [setMicMuted, showErrorToast]);

  const toggleCamera = useCallback(() => {
    const controller = trackControllerRef.current;
    if (!controller) return;
    const previousEnabled = controller.getCameraEnabled();
    const nextEnabled = !previousEnabled;

    setCameraOff(!nextEnabled);

    controller.setCameraEnabled(nextEnabled).catch((err) => {
      setCameraOff(previousEnabled);
      showErrorToast(err);
    });
  }, [setCameraOff, showErrorToast]);

  const syncFromServer = useCallback(
    (next: { micMuted: boolean; cameraOff: boolean }) => {
      const controller = trackControllerRef.current;
      if (!controller) {
        setMicMuted(next.micMuted);
        setCameraOff(next.cameraOff);
        return;
      }

      const previous = controller.getState();
      controller.hydrate(!next.micMuted, !next.cameraOff);
      setMicMuted(next.micMuted);
      setCameraOff(next.cameraOff);
      controller
        .syncTrackAlignment()
        .catch((err) => {
          controller.hydrate(previous.micEnabled, previous.cameraEnabled);
          setMicMuted(previous.micEnabled);
          setCameraOff(previous.cameraEnabled);
          showErrorToast(err);
        });
    },
    [setMicMuted, setCameraOff, showErrorToast],
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
    trackControllerRef.current = null;
    lastSyncedMediaRef.current = null;
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
