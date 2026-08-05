"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { JoinLobby } from "./join-lobby";
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
} from "../../lib/liveroom-storage";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { JoinRequest, RoomLookup } from "../../types";

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
  const request =
    createdRequest ??
    (recovered && recovered.state !== "CANCELLED" && recovered.state !== "EXPIRED"
      ? recovered
      : null);

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
      <div className="flex items-center justify-center gap-2 py-16 text-sm text-neutral-500 dark:text-neutral-400">
        <Spinner size="sm" />
        {t("looking")}
      </div>
    );
  }

  if (!lookup) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-xl border border-neutral-200 bg-white p-6 text-center md:p-8 dark:border-neutral-800 dark:bg-black">
        <p className="text-lg font-semibold text-black dark:text-white">{t("notFound")}</p>
        {lookupFailed ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {resolveLiveroomErrorMessage(lookupError, tErrors, tCommon)}
          </p>
        ) : null}
        <Button
          variant="outline"
          className="h-11 md:h-9"
          onClick={() => router.push("/dashboard/liveroom/join")}
        >
          {t("continue")}
        </Button>
      </div>
    );
  }

  const unavailable = lookup.status === "ENDED";

  return (
    <div className="flex flex-col gap-6">
      <RoomLookupSummary lookup={lookup} />

      {recovering && !request ? (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-neutral-500 dark:text-neutral-400">
          <Spinner size="sm" />
          {t("looking")}
        </div>
      ) : request ? (
        <JoinLobby roomId={roomId} initialRequest={request} onRetry={restart} />
      ) : (
        <PreJoinPanel
          media={media}
          submitting={asking}
          disabled={unavailable}
          onSubmit={() => {
            media.stopAll();
            askToJoin({
              roomId,
              data: { idempotencyKey: getOrCreateIdempotencyKey(roomId) },
            });
          }}
        />
      )}
    </div>
  );
}