"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Clock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useCancelJoinRequest } from "../api/join-requests";
import { subscribeUserJoinRequestDecisions, requestRoomState } from "../api/ws";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoomJoinRequest } from "../types";

interface WaitingRoomCardProps {
  roomCode: string;
  request: LiveRoomJoinRequest;
  onApproved: () => void;
  onRejected: (reason: string) => void;
  onCancelled: () => void;
}

export function WaitingRoomCard({
  roomCode,
  request,
  onApproved,
  onRejected,
  onCancelled,
}: WaitingRoomCardProps) {
  const t = useTranslations("liveroom.waitingRoom");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");

  const [seconds, setSeconds] = useState(() =>
    Math.max(0, Math.floor((Date.now() - new Date(request.createdAt).getTime()) / 1000)),
  );

  useEffect(() => {
    const id = window.setInterval(() => {
      setSeconds(
        Math.max(
          0,
          Math.floor((Date.now() - new Date(request.createdAt).getTime()) / 1000),
        ),
      );
    }, 1000);
    return () => window.clearInterval(id);
  }, [request.createdAt]);

  useEffect(() => {
    const handle = subscribeUserJoinRequestDecisions((event) => {
      if (event.requestId !== request.id) return;
      if (event.status === "APPROVED") {
        onApproved();
        setTimeout(() => {
          requestRoomState(roomCode);
        }, 300);
      } else if (event.status === "REJECTED") {
        onRejected(event.reason ?? "");
      } else if (event.status === "CANCELLED") {
        onCancelled();
      }
    });
    return () => handle.unsubscribe();
  }, [request.id, roomCode, onApproved, onRejected, onCancelled]);

  const { mutate: cancelJoinRequest, isPending } = useCancelJoinRequest({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          onCancelled();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const handleCancel = () => {
    cancelJoinRequest({ roomCode, requestId: request.id });
  };

  const elapsed = seconds < 60 ? t("elapsedSeconds", { seconds }) : t("elapsedMinutes", { minutes: Math.floor(seconds / 60) });

  return (
    <div className="flex flex-col items-center gap-4 rounded-xl border border-neutral-200 bg-white p-8 text-center dark:border-neutral-800 dark:bg-black">
      <div className="flex size-14 items-center justify-center rounded-full bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
        <Clock className="size-7" />
      </div>
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-black dark:text-white">
          {t("title")}
        </h2>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>
      <p className="text-xs text-neutral-500 dark:text-neutral-400">{elapsed}</p>
      <p className="text-xs text-neutral-500 dark:text-neutral-400">
        {t("timeoutHint")}
      </p>
      <Button
        variant="outline"
        onClick={handleCancel}
        disabled={isPending}
        className="min-w-[160px]"
      >
        {isPending ? (
          <span className="flex items-center gap-2">
            <Spinner size="sm" />
            {t("cancelling")}
          </span>
        ) : (
          t("cancelBtn")
        )}
      </Button>
    </div>
  );
}
