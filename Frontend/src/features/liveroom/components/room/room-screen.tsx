"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { ConnectionBadge } from "./connection-badge";
import { KickedScreen } from "./kicked-screen";
import { LeaveRoomDialog } from "./leave-room-dialog";
import { OwnerAbsentBanner } from "./owner-absent-banner";
import { RoomControlBar } from "./room-control-bar";
import { RoomEndingOverlay } from "./room-ending-overlay";
import { RoomHeader } from "./room-header";
import { RoomSidePanel, type SidePanelTab } from "./room-side-panel";
import { TabConflictScreen } from "./tab-conflict-screen";
import { VideoGrid } from "../video/video-grid";
import { EndRoomDialog } from "../room-list/end-room-dialog";
import { useLeaveRoom, useUpdateMediaState } from "../../api/participants";
import { useAudioLevel } from "../../hooks/use-audio-level";
import { useLocalMedia } from "../../hooks/use-local-media";
import type { MediaErrorKind } from "../../lib/media-constraints";
import { useLiveroomSocket } from "../../hooks/use-liveroom-socket";
import { usePeerMesh } from "../../hooks/use-peer-mesh";
import { useRoomSession } from "../../hooks/use-room-session";
import { useRoomTabLock } from "../../hooks/use-room-tab-lock";
import { liveroomSocket } from "../../lib/liveroom-socket";
import {
  clearMediaIntent,
  readKicked,
  readMediaIntent,
  writeKicked,
} from "../../lib/liveroom-storage";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import type { Room } from "../../types";


const MIC_SILENCE_MS = 6000;

const DEVICE_ERROR_KEY: Record<MediaErrorKind, string> = {
  denied: "permissionDenied",
  notFound: "noDevice",
  busy: "deviceBusy",
  overconstrained: "noDevice",
  unsupported: "noDevice",
  unknown: "noDevice",
};

export function RoomScreen({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.connection");
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tLobby = useTranslations("liveroom.lobby");
  const tPrejoin = useTranslations("liveroom.prejoin");
  const t2 = useTranslations("liveroom.room.video");
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const myUserId = user?.userId ?? null;

  const [cachedKick] = useState(() => (roomId ? readKicked(roomId) : null));
  const tabLock = useRoomTabLock(roomId);
  const active = tabLock.state === "owner" && !cachedKick && Boolean(myUserId);

  useLiveroomSocket();
  const { phase, errorCode, retry } = useRoomSession(active ? roomId : "", myUserId);

  const media = useLocalMedia();
  usePeerMesh({ roomId, enabled: active && phase === "ready", media });

  const kicked = useLiveroomStore((state) => state.lifecycle.kicked);
  const room = useLiveroomStore((state) => state.room);
  const myParticipant = useLiveroomStore((state) =>
    myUserId ? state.participants[myUserId] : undefined,
  );

  const [panelOpen, setPanelOpen] = useState(false);
  const [panelTab, setPanelTab] = useState<SidePanelTab>("participants");
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [endOpen, setEndOpen] = useState(false);

  useEffect(() => {
    if (!kicked) return;
    writeKicked(roomId, {
      kickedAt: new Date().toISOString(),
      cooldownUntil: kicked.cooldownUntil,
      reason: kicked.reason,
    });
  }, [kicked, roomId]);

  const { mutate: leave, isPending: leaving } = useLeaveRoom({
    mutationConfig: {
      onSuccess: () => {
        liveroomSocket.disconnect();
        router.push("/dashboard/liveroom");
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
        liveroomSocket.disconnect();
        router.push("/dashboard/liveroom");
      }),
    },
  });

  const { mutate: patchMedia, isPending: patchingMedia } = useUpdateMediaState({
    mutationConfig: {
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const goHome = useCallback(() => {
    liveroomSocket.disconnect();
    router.push("/dashboard/liveroom");
  }, [router]);

  const toggleCamera = async () => {
    if (media.cameraOn) {
      media.disableCamera();
      patchMedia({ roomId, data: { cameraOn: false } });
      return;
    }
    const ok = await media.enableCamera();
    if (ok) patchMedia({ roomId, data: { cameraOn: true } });
  };

  const toggleMic = async () => {
    if (media.micOn) {
      media.disableMic();
      patchMedia({ roomId, data: { micOn: false } });
      return;
    }
    const ok = await media.enableMic();
    if (ok) patchMedia({ roomId, data: { micOn: true } });
  };

  const mediaRef = useRef(media);
  const patchMediaRef = useRef(patchMedia);
  const intentAppliedRef = useRef(false);

  useEffect(() => {
    mediaRef.current = media;
    patchMediaRef.current = patchMedia;
  });

  useEffect(() => {
    if (phase !== "ready" || !roomId || intentAppliedRef.current) return;
    intentAppliedRef.current = true;

    const intent = readMediaIntent(roomId);
    clearMediaIntent(roomId);
    if (!intent || (!intent.cameraOn && !intent.micOn)) return;

    void (async () => {
      const cameraOn = intent.cameraOn ? await mediaRef.current.enableCamera() : false;
      const micOn = intent.micOn ? await mediaRef.current.enableMic() : false;
      if (!cameraOn && !micOn) return;
      patchMediaRef.current({
        roomId,
        data: {
          ...(cameraOn ? { cameraOn: true } : {}),
          ...(micOn ? { micOn: true } : {}),
        },
      });
    })();
  }, [phase, roomId]);

  const localAudioLevel = useAudioLevel(media.stream);
  const levelRef = useRef(localAudioLevel);
  const lastSoundRef = useRef(0);
  const [micSilent, setMicSilent] = useState(false);

  useEffect(() => {
    levelRef.current = localAudioLevel;
  }, [localAudioLevel]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const current = mediaRef.current;
      if (!current.micOn || levelRef.current > 0 || lastSoundRef.current === 0) {
        lastSoundRef.current = Date.now();
      }
      setMicSilent(current.micOn && Date.now() - lastSoundRef.current > MIC_SILENCE_MS);
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  const mediaError = media.error;
  const reportedErrorRef = useRef<string | null>(null);

  useEffect(() => {
    if (!mediaError || reportedErrorRef.current === mediaError) return;
    reportedErrorRef.current = mediaError;
    toast.error(tPrejoin(DEVICE_ERROR_KEY[mediaError]));
  }, [mediaError, tPrejoin]);

  const remoteMuted = myParticipant?.micState === "MUTED_BY_OWNER";

  useEffect(() => {
    if (!remoteMuted) return;
    mediaRef.current.disableMic();
  }, [remoteMuted]);

  if (tabLock.state === "conflict") {
    return (
      <TabConflictScreen
        onFocusOther={tabLock.requestFocusOnOwner}
        onTakeOver={tabLock.takeOver}
      />
    );
  }

  if (cachedKick || kicked) {
    return <KickedScreen cooldownUntil={(kicked ?? cachedKick)?.cooldownUntil ?? null} />;
  }

  if (phase === "denied" || phase === "kicked" || phase === "ended" || phase === "error") {
    return (
      <div className="flex h-dvh flex-col items-center justify-center gap-4 bg-white p-6 text-center dark:bg-black">
        <p className="text-lg font-semibold text-black dark:text-white">
          {resolveLiveroomErrorMessage({ code: errorCode }, tErrors, tCommon)}
        </p>
        <div className="flex flex-col gap-2 sm:flex-row">
          {phase === "denied" ? (
            <Button
              className="h-11 md:h-9"
              onClick={() => router.push("/dashboard/liveroom/join")}
            >
              {tLobby("backToJoin")}
            </Button>
          ) : (
            <Button className="h-11 md:h-9" onClick={retry}>
              {t("retry")}
            </Button>
          )}
          <Button variant="outline" className="h-11 md:h-9" onClick={goHome}>
            {tLobby("backToJoin")}
          </Button>
        </div>
      </div>
    );
  }

  if (!myUserId || phase !== "ready") {
    return (
      <div className="flex h-dvh flex-col items-center justify-center gap-3 bg-white dark:bg-black">
        <Spinner size="sm" />
        <ConnectionBadge />
      </div>
    );
  }

  const micBlocked = remoteMuted;

  return (
    <div className="relative flex h-dvh flex-col overflow-hidden bg-white dark:bg-black">
      <RoomHeader />
      <OwnerAbsentBanner />

      {micSilent ? (
        <div
          role="status"
          className="flex items-start gap-2 border-b border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200"
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            {t2("micSilent")}
            {media.audioTrack?.label ? ` (${media.audioTrack.label})` : ""}
          </span>
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1">
        <VideoGrid
          localStream={media.stream}
          localCameraOn={media.cameraOn}
          localAudioLevel={localAudioLevel}
        />
        <RoomSidePanel
          roomId={roomId}
          open={panelOpen}
          tab={panelTab}
          onTabChange={setPanelTab}
          onClose={() => setPanelOpen(false)}
        />
      </div>

      <RoomControlBar
        cameraOn={media.cameraOn}
        micOn={media.micOn}
        micBlocked={micBlocked}
        busy={media.requesting || patchingMedia}
        onToggleCamera={() => void toggleCamera()}
        onToggleMic={() => void toggleMic()}
        onOpenParticipants={() => {
          setPanelTab("participants");
          setPanelOpen(true);
        }}
        onOpenChat={() => {
          setPanelTab("chat");
          setPanelOpen(true);
        }}
        onLeave={() => setLeaveOpen(true)}
        onEnd={() => setEndOpen(true)}
      />

      <RoomEndingOverlay onLeave={goHome} />

      <LeaveRoomDialog
        open={leaveOpen}
        pending={leaving}
        onOpenChange={setLeaveOpen}
        onConfirm={() => leave({ roomId })}
      />

      <EndRoomDialog
        room={
          {
            id: roomId,
            roomName: room.roomName,
            canUndoEnd: true,
          } as Room
        }
        open={endOpen}
        onOpenChange={setEndOpen}
      />
    </div>
  );
}