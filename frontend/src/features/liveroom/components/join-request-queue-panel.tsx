"use client";

import { useCallback, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import {
  liveRoomJoinRequestsKey,
  useApproveJoinRequest,
  useListJoinRequests,
  useRejectJoinRequest,
} from "../api/join-requests";
import { useLiveRoomRealtime } from "../hooks/use-live-room-realtime";
import { DeclineRequestDialog } from "./decline-request-dialog";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoomJoinRequest } from "../types";

interface JoinRequestQueuePanelProps {
  roomCode: string;
}

function timeAgo(iso: string, t: ReturnType<typeof useTranslations>): string {
  const created = new Date(iso).getTime();
  if (Number.isNaN(created)) return iso;
  const diffSec = Math.max(0, Math.floor((Date.now() - created) / 1000));
  if (diffSec < 10) return t("justNow");
  if (diffSec < 60) return t("requestedAgo", { time: `${diffSec}s` });
  const minutes = Math.floor(diffSec / 60);
  return t("requestedAgo", { time: `${minutes}m` });
}

export function JoinRequestQueuePanel({ roomCode }: JoinRequestQueuePanelProps) {
  const t = useTranslations("liveroom.hostWaitingRoom");
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tAdmission = useTranslations("liveroom.participant");
  const queryClient = useQueryClient();

  const [declineTarget, setDeclineTarget] = useState<LiveRoomJoinRequest | null>(null);

  const { data, isLoading, isError } = useListJoinRequests({
    roomCode,
    status: "PENDING",
  });
  const items: LiveRoomJoinRequest[] = data?.success && data.data ? data.data : [];

  useLiveRoomRealtime({
    roomCode,
    isHost: true,
    onJoinRequestCreated: (event) => {
      const newRequest: LiveRoomJoinRequest = {
        id: event.requestId,
        roomCode: event.roomCode,
        userId: event.requesterUserId,
        displayName: event.displayName,
        message: event.message || null,
        status: "PENDING",
        decisionReason: null,
        decidedByUserId: null,
        decidedAt: null,
        createdAt: event.timestamp,
      };
      queryClient.setQueryData(
        liveRoomJoinRequestsKey(roomCode, "PENDING"),
        (old: unknown) => {
          if (!old || typeof old !== "object" || !("data" in old)) {
            return { success: true, data: [newRequest] };
          }
          const list = ((old as { data?: LiveRoomJoinRequest[] }).data ?? []).slice();
          if (list.some((r) => r.id === newRequest.id)) return old;
          list.unshift(newRequest);
          return { ...(old as Record<string, unknown>), data: list };
        },
      );
      toast.success(t("hostToastNewRequest", { name: event.displayName }));
    },
  });

  const showError = useCallback(
    (err: unknown) => {
      toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
    },
    [tErrors, tCommon],
  );

  const { mutate: approveJoinRequest, isPending: isApproving } = useApproveJoinRequest({
    mutationConfig: {
      onError: asApiError(showError),
    },
  });

  const { mutate: rejectJoinRequest, isPending: isRejecting } = useRejectJoinRequest({
    mutationConfig: {
      onError: asApiError(showError),
    },
  });

  const handleApprove = (req: LiveRoomJoinRequest) => {
    approveJoinRequest({ roomCode, requestId: req.id });
  };

  const handleDeclineClick = (req: LiveRoomJoinRequest) => {
    setDeclineTarget(req);
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 p-6 text-sm text-neutral-500">
        <Spinner size="sm" />
        <span>{tCommon("loading")}</span>
      </div>
    );
  }

  if (isError && items.length === 0) {
    return (
      <p className="text-xs text-red-600 dark:text-red-400">{tErrors("generic")}</p>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-black dark:text-white">
          {t("title")}
        </h2>
        <span className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("count", { count: items.length })}
        </span>
      </div>

      {items.length === 0 ? (
        <div className="rounded-lg border border-dashed border-neutral-200 p-6 text-center text-xs text-neutral-500 dark:border-neutral-800 dark:text-neutral-400">
          {t("empty")}
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((req) => (
            <li
              key={req.id}
              className="flex flex-col gap-2 rounded-lg border border-neutral-200 p-4 dark:border-neutral-800"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex flex-col">
                  <span className="text-sm font-semibold text-black dark:text-white">
                    {req.displayName}
                  </span>
                  <span className="text-xs text-neutral-500 dark:text-neutral-400">
                    {tAdmission("roleLabel")}: {req.userId}
                  </span>
                </div>
                <span className="text-xs text-neutral-500 dark:text-neutral-400">
                  {timeAgo(req.createdAt, t)}
                </span>
              </div>
              {req.message && (
                <p className="text-sm text-neutral-700 dark:text-neutral-300">
                  {req.message}
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  disabled={isApproving || isRejecting}
                  onClick={() => handleApprove(req)}
                >
                  {isApproving ? (
                    <span className="flex items-center gap-2">
                      <Spinner size="sm" />
                      {t("admitting")}
                    </span>
                  ) : (
                    t("admitBtn")
                  )}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={isApproving || isRejecting}
                  onClick={() => handleDeclineClick(req)}
                >
                  {t("declineBtn")}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <DeclineRequestDialog
        roomCode={roomCode}
        request={declineTarget}
        open={declineTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeclineTarget(null);
        }}
        onConfirm={(reason) => {
          if (!declineTarget) return;
          const trimmed = reason?.trim();
          rejectJoinRequest(
            {
              roomCode,
              requestId: declineTarget.id,
              data: trimmed ? { reason: trimmed } : {},
            },
            {
              onSuccess: () => {
                setDeclineTarget(null);
              },
            },
          );
        }}
      />
    </div>
  );
}
