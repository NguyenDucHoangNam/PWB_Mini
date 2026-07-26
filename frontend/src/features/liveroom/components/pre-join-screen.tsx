"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Mic, MicOff, Video, VideoOff } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useMediaDevices } from "../hooks/use-media-devices";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import { useJoinAsHost, useJoinPublicRoom } from "../api/participants";
import { useCreateJoinRequest } from "../api/join-requests";
import { useMicLevelMeter } from "../hooks/use-mic-level-meter";
import { applyTrackMutedFlag, useLiveRoomMediaStore } from "../stores/use-live-room-media-store";
import { MicLevelIndicator } from "./mic-level-indicator";
import type { LiveRoom, LiveRoomJoinRequest } from "../types";

interface PreJoinScreenProps {
  room: LiveRoom;
  mode: "HOST" | "PUBLIC" | "PRIVATE";
  onJoinedPublic: () => void;
  onJoinedAsHost?: () => void;
  onRequestSent: (request: LiveRoomJoinRequest) => void;
}

export function PreJoinScreen({
  room,
  mode,
  onJoinedPublic,
  onJoinedAsHost,
  onRequestSent,
}: PreJoinScreenProps) {
  const t = useTranslations("liveroom.preJoin");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tMedia = useTranslations("liveroom.media");
  const tExists = useTranslations("liveroom.existsCheck");

  const devices = useMediaDevices();
  const previewVideoRef = useRef<HTMLVideoElement | null>(null);
  const [micMuted, setMicMuted] = useState(false);
  const [cameraBusy, setCameraBusy] = useState(false);
  const [micBusy, setMicBusy] = useState(false);
  const [previewReady, setPreviewReady] = useState(false);
  const [cameraEnabled, setCameraEnabled] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        await devices.start();
      } catch {
        /* surfaced via store.errorMessage */
      } finally {
        if (!cancelled) setPreviewReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const video = previewVideoRef.current;
    if (!video || !previewReady) return;
    if (cameraEnabled) {
      video.srcObject = devices.stream;
    } else {
      video.srcObject = null;
    }
  }, [devices.stream, previewReady, cameraEnabled]);

  const micLevel = useMicLevelMeter(devices.stream, !micMuted && previewReady);

  useEffect(() => {
    if (!previewReady) return;
    applyTrackMutedFlag(devices.stream, "audio", micMuted);
  }, [micMuted, devices.stream, previewReady]);

  useEffect(() => {
    return () => {
      const video = previewVideoRef.current;
      if (video) {
        video.pause();
        video.srcObject = null;
      }
      devices.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isActive = room.status === "ACTIVE";

  const setMediaStoreState = () => {
    useLiveRoomMediaStore.getState().setMicMuted(micMuted);
    useLiveRoomMediaStore.getState().setCameraOff(!cameraEnabled);
  };

  const { mutate: joinPublicRoom, isPending: isJoiningPublic } = useJoinPublicRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          setMediaStoreState();
          devices.stop();
          onJoinedPublic();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: joinAsHost, isPending: isJoiningHost } = useJoinAsHost({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          setMediaStoreState();
          devices.stop();
          onJoinedAsHost?.();
          onJoinedPublic();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: createJoinRequest, isPending: isRequesting } = useCreateJoinRequest({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success && response.data) {
          setMediaStoreState();
          devices.stop();
          onRequestSent(response.data);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const handleToggleCamera = async () => {
    if (cameraBusy) return;
    setCameraBusy(true);

    const video = previewVideoRef.current;
    if (video) {
      video.pause();
      video.srcObject = null;
    }

    try {
      if (cameraEnabled) {
        await devices.disableCamera();
        setCameraEnabled(false);
      } else {
        await devices.enableCamera();
        setCameraEnabled(true);
      }
    } catch (err) {
      toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
    } finally {
      setCameraBusy(false);
    }
  };

  const handleToggleMic = () => {
    if (micBusy) return;
    setMicBusy(true);
    setMicMuted((v) => !v);
    setMicBusy(false);
  };

  const handleJoin = () => {
    if (mode === "HOST") {
      joinAsHost({
        roomCode: room.roomCode,
        body: { micMuted, cameraOff: !cameraEnabled },
      });
    } else if (mode === "PUBLIC") {
      joinPublicRoom({
        roomCode: room.roomCode,
        body: { micMuted, cameraOff: !cameraEnabled },
      });
    } else {
      createJoinRequest({ roomCode: room.roomCode });
    }
  };

  const isPending = isJoiningPublic || isJoiningHost || isRequesting;

  return (
    <div className="flex h-screen flex-col bg-neutral-950">
      <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-4 py-3">
        <div className="flex items-center gap-2 rounded-full bg-black/60 px-3 py-1.5 backdrop-blur-sm">
          <span className="text-sm font-medium text-white">{room.title}</span>
          <span className="font-mono text-xs text-neutral-400">{room.roomCode}</span>
        </div>
      </div>

      <div className="flex flex-1 items-center justify-center px-4">
        <div className="flex w-full max-w-lg flex-col items-center gap-6">
          <div className="relative aspect-video w-full overflow-hidden rounded-2xl bg-neutral-900 ring-1 ring-white/10">
            {cameraEnabled && devices.stream ? (
              <video
                ref={previewVideoRef}
                autoPlay
                playsInline
                muted
                className="h-full w-full object-cover [transform:scaleX(-1)]"
              />
            ) : (
              <div className="flex h-full w-full flex-col items-center justify-center gap-3">
                <div className="flex size-20 items-center justify-center rounded-full bg-neutral-800 text-2xl font-bold uppercase text-neutral-400">
                  {(room.title || "?").charAt(0)}
                </div>
                <span className="text-sm text-neutral-500">
                  {cameraBusy ? tCommon("loading") : tMedia("cameraOff")}
                </span>
              </div>
            )}

            <div className="pointer-events-none absolute inset-x-3 bottom-3 flex items-center justify-between gap-3">
              <div className="flex items-center gap-2 rounded-full bg-black/55 px-3 py-1.5 backdrop-blur-md ring-1 ring-white/10">
                {micMuted ? (
                  <MicOff className="size-3.5 text-red-400" />
                ) : (
                  <Mic className="size-3.5 text-emerald-400" />
                )}
                <MicLevelIndicator
                  level={micLevel}
                  active={!micMuted && previewReady && !!devices.stream}
                />
              </div>
              {!cameraEnabled ? (
                <div className="flex items-center gap-1.5 rounded-full bg-red-500/85 px-3 py-1.5 text-xs font-medium text-white ring-1 ring-white/15">
                  <VideoOff className="size-3.5" />
                  <span>{tMedia("cameraOff")}</span>
                </div>
              ) : null}
            </div>

            {cameraBusy ? (
              <div className="absolute inset-0 flex items-center justify-center bg-black/40 backdrop-blur-sm">
                <Spinner size="md" />
              </div>
            ) : null}
          </div>

          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={handleToggleMic}
              aria-label={micMuted ? tMedia("micOff") : tMedia("micOn")}
              title={micMuted ? tMedia("micOff") : tMedia("micOn")}
              className={`inline-flex size-12 items-center justify-center rounded-full transition ${
                micMuted
                  ? "bg-red-500 text-white hover:bg-red-600"
                  : "bg-neutral-700 text-white hover:bg-neutral-600"
              }`}
            >
              {micMuted ? <MicOff className="size-5" /> : <Mic className="size-5" />}
            </button>
            <button
              type="button"
              onClick={handleToggleCamera}
              disabled={cameraBusy}
              aria-label={cameraEnabled ? tMedia("cameraOff") : tMedia("cameraOn")}
              title={cameraEnabled ? tMedia("cameraOff") : tMedia("cameraOn")}
              className={`inline-flex size-12 items-center justify-center rounded-full transition ${
                !cameraEnabled
                  ? "bg-red-500 text-white hover:bg-red-600"
                  : "bg-neutral-700 text-white hover:bg-neutral-600"
              }`}
            >
              {cameraEnabled ? <Video className="size-5" /> : <VideoOff className="size-5" />}
            </button>
          </div>

          <p className="text-center text-xs text-neutral-500">
            {micMuted ? tMedia("micOff") : tMedia("micOn")}
            {" • "}
            {cameraEnabled ? tMedia("cameraOn") : tMedia("cameraOff")}
          </p>

          <div className="flex flex-col items-center gap-1 text-center">
            <h1 className="text-2xl font-bold text-white">{room.title}</h1>
            {room.description && (
              <p className="max-w-md text-sm text-neutral-400">{room.description}</p>
            )}
            <p className="text-xs text-neutral-500">
              {tExists("existsActive")} • {room.currentParticipantCount}/{room.maxParticipants} participants
            </p>
          </div>

          {!isActive ? (
            <p className="text-sm text-neutral-400">{t("roomNotActive")}</p>
          ) : (
            <Button
              size="lg"
              disabled={isPending}
              onClick={handleJoin}
              className="min-w-[200px]"
            >
              {isPending ? (
                <span className="flex items-center gap-2">
                  <Spinner size="sm" />
                  {mode === "PRIVATE" ? t("requesting") : t("joining")}
                </span>
              ) : mode === "PRIVATE" ? (
                t("askBtn")
              ) : (
                t("joinBtn")
              )}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
