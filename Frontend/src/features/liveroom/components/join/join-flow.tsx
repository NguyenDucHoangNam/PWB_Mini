"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { DoorClosed } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { JoinLobby } from "./join-lobby";
import { JoinStateCard } from "./join-state-card";
import { PreJoinPanel } from "./pre-join-panel";
import { RoomLookupSummary } from "./room-lookup-summary";
import { useFindRoomByCode } from "../../api/rooms";
import { useCreateJoinRequest, useMyJoinRequest } from "../../api/join-requests";
import { useLocalMedia } from "../../hooks/use-local-media";
import { LiveroomErrorCode } from "../../lib/liveroom-error-codes";
import {
  clearIdempotencyKey,
  getOrCreateIdempotencyKey,
  readLookup,
  writeLookup,
  writeMediaIntent,
} from "../../lib/liveroom-storage";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { JoinRequest, JoinRequestState, RoomLookup } from "../../types";

const RESUMABLE_STATES: ReadonlySet<JoinRequestState> = new Set<JoinRequestState>([
  "PENDING",
  "APPROVED",
  "LOCKED",
]);

export function JoinFlow({ roomCode }: { roomCode: string }) {
  const t = useTranslations("liveroom.join");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const router = useRouter();
  const media = useLocalMedia();

  const cached = useMemo(() => readLookup(roomCode), [roomCode]);
  const [createdRequest, setCreatedRequest] = useState<JoinRequest | null>(null);

  const {
    data: lookupResponse,
    isPending: lookingUp,
    isError: lookupFailed,
    error: lookupError,
  } = useFindRoomByCode({ roomCode, enabled: !cached });

  const lookup: RoomLookup | null = cached ?? lookupResponse?.data ?? null;

  useEffect(() => {
    const found = lookupResponse?.data;
    if (found) writeLookup(roomCode, found);
  }, [lookupResponse, roomCode]);

  const roomId = lookup?.roomId ?? "";

  const { data: myRequestResponse, isPending: recovering } = useMyJoinRequest({
    roomId,
    enabled: Boolean(roomId) && !createdRequest,
  });

  const recovered = myRequestResponse?.data;
  const staleDecision = recovered ? !RESUMABLE_STATES.has(recovered.state) : false;
  const request = createdRequest ?? (recovered && !staleDecision ? recovered : null);

  useEffect(() => {
    if (staleDecision && roomId) clearIdempotencyKey(roomId);
  }, [staleDecision, roomId]);

  const { mutate: askToJoin, isPending: asking } = useCreateJoinRequest({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.data) setCreatedRequest(response.data);
      },
      onError: asApiError((err) => {
        const code = "code" in err ? err.code : undefined;
        if (code === LiveroomErrorCode.ALREADY_APPROVED) {
          router.push(`/liveroom/${roomId}`);
          return;
        }
        if (
          code === LiveroomErrorCode.REQUEST_LOCKED ||
          code === LiveroomErrorCode.KICKED_COOLDOWN
        ) {
          clearIdempotencyKey(roomId);
        }
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const restart = () => {
    setCreatedRequest(null);
    clearIdempotencyKey(roomId);
    router.push("/dashboard/liveroom/join");
  };

  if (!lookup && lookingUp) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
        <Spinner size="sm" />
        {t("looking")}
      </div>
    );
  }

  if (!lookup) {
    return (
      <div className="flex h-full flex-col">
        <div className="flex flex-1 flex-col items-center justify-center gap-4 rounded-xl border border-border bg-card p-6 text-center shadow-xs md:p-8">
          <div className="flex flex-col gap-1">
            <p className="text-lg font-bold tracking-tight text-foreground">{t("notFound")}</p>
            {lookupFailed ? (
              <p className="text-sm text-muted-foreground">
                {resolveLiveroomErrorMessage(lookupError, tErrors, tCommon)}
              </p>
            ) : null}
          </div>
          <Button
            variant="outline"
            className="h-11 md:h-9"
            onClick={() => router.push("/dashboard/liveroom/join")}
          >
            {t("continue")}
          </Button>
        </div>
      </div>
    );
  }

  const unavailable = lookup.status === "ENDED";

  return (
    <div className="flex h-full flex-col gap-4">
      <RoomLookupSummary lookup={lookup} />

      <div className="flex min-h-0 flex-1 flex-col">
        {unavailable ? (
          <JoinStateCard
            tone="danger"
            icon={<DoorClosed className="size-8" aria-hidden />}
            title={t("ended")}
            body={t("endedHint")}
            action={
              <Button
                variant="outline"
                className="h-11 md:h-9"
                onClick={() => router.push("/dashboard/liveroom/join")}
              >
                {t("tryAnotherCode")}
              </Button>
            }
          />
        ) : recovering && !request ? (
          <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
            <Spinner size="sm" />
            {t("looking")}
          </div>
        ) : request ? (
          <JoinLobby roomId={roomId} initialRequest={request} onRetry={restart} />
        ) : (
          <PreJoinPanel
            media={media}
            submitting={asking}
            onSubmit={() => {
              writeMediaIntent(roomId, { cameraOn: media.cameraOn, micOn: media.micOn });
              media.stopAll();
              askToJoin({
                roomId,
                data: { idempotencyKey: getOrCreateIdempotencyKey(roomId) },
              });
            }}
          />
        )}
      </div>
    </div>
  );
}