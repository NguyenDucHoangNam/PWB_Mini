"use client";

import { useCallback, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import { useMediaDevices } from "./use-media-devices";
import { useUpdateMyMedia } from "../api/participants";
import { subscribeRoomParticipants } from "../api/ws";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";

interface UseImmersiveMediaControlsParams {
  roomCode: string;
  localUserId: string | null;
}

export function useImmersiveMediaControls({
  roomCode,
  localUserId,
}: UseImmersiveMediaControlsParams) {
  const setMicMuted = useLiveRoomMediaStore((state) => state.setMicMuted);
  const setCameraOff = useLiveRoomMediaStore((state) => state.setCameraOff);
  const devices = useMediaDevices();

  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");

  const updateMyMediaMutation = useUpdateMyMedia({
    mutationConfig: {
      onError: (err) => {
        const initialMic = initialMicRef.current;
        const initialCamera = initialCameraRef.current;
        if (initialMic !== null) {
          setMicMuted(initialMic);
        }
        if (initialCamera !== null) {
          setCameraOff(initialCamera);
        }
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      },
    },
  });

  const initialMicRef = useRef<boolean | null>(null);
  const initialCameraRef = useRef<boolean | null>(null);

  const joinedReadyRef = useRef(false);
  const pendingMediaRef = useRef<{ micMuted: boolean; cameraOff: boolean } | null>(null);
  const mutationRef = useRef(updateMyMediaMutation);
  useEffect(() => {
    mutationRef.current = updateMyMediaMutation;
  }, [updateMyMediaMutation]);

  useEffect(() => {
    if (!roomCode || !localUserId) return undefined;
    joinedReadyRef.current = false;
    const subscription = subscribeRoomParticipants(roomCode, (event) => {
      if (event.type === "PARTICIPANT_JOINED" && event.userId === localUserId) {
        joinedReadyRef.current = true;
        const pending = pendingMediaRef.current;
        if (pending) {
          pendingMediaRef.current = null;
          mutationRef.current.mutate({ roomCode, body: pending });
        }
        return;
      }
    });
    const fallback = setTimeout(() => {
      joinedReadyRef.current = true;
    }, 1500);
    return () => {
      subscription.unsubscribe();
      clearTimeout(fallback);
      joinedReadyRef.current = false;
      pendingMediaRef.current = null;
    };
  }, [roomCode, localUserId]);

  const toggleMic = useCallback(() => {
    const store = useLiveRoomMediaStore.getState();
    const before = { micMuted: store.micMuted, cameraOff: store.cameraOff };
    const next = { micMuted: !before.micMuted, cameraOff: before.cameraOff };
    initialMicRef.current = before.micMuted;
    initialCameraRef.current = before.cameraOff;
    setMicMuted(next.micMuted);
    const stream = devices.stream;
    if (stream) {
      for (const track of stream.getAudioTracks()) {
        track.enabled = !next.micMuted;
      }
    }
    if (!roomCode) return;
    if (joinedReadyRef.current) {
      updateMyMediaMutation.mutate({ roomCode, body: next });
    } else {
      pendingMediaRef.current = next;
    }
  }, [devices.stream, roomCode, setMicMuted, updateMyMediaMutation]);

  const toggleCamera = useCallback(async () => {
    const store = useLiveRoomMediaStore.getState();
    const before = { micMuted: store.micMuted, cameraOff: store.cameraOff };
    const next = { micMuted: before.micMuted, cameraOff: !before.cameraOff };
    initialMicRef.current = before.micMuted;
    initialCameraRef.current = before.cameraOff;
    setCameraOff(next.cameraOff);
    try {
      if (next.cameraOff) {
        await devices.disableCamera();
      } else {
        await devices.enableCamera();
      }
    } catch (err) {
      setCameraOff(before.cameraOff);
      toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      return;
    }
    if (!roomCode) return;
    if (joinedReadyRef.current) {
      updateMyMediaMutation.mutate({ roomCode, body: next });
    } else {
      pendingMediaRef.current = next;
    }
  }, [devices, roomCode, setCameraOff, updateMyMediaMutation, tErrors, tCommon]);

  return {
    micMuted: useLiveRoomMediaStore((state) => state.micMuted),
    cameraOff: useLiveRoomMediaStore((state) => state.cameraOff),
    toggleMic,
    toggleCamera,
  };
}
