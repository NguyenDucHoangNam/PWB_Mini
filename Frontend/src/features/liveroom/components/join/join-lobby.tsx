"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Ban, Clock, Loader2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { AutoJoinCountdown } from "./auto-join-countdown";
import { JoinStepIndicator } from "./join-step-indicator";
import {
  myJoinRequestKey,
  useCancelJoinRequest,
  useMyJoinRequest,
} from "../../api/join-requests";
import { liveroomSocket } from "../../lib/liveroom-socket";
import { clearIdempotencyKey } from "../../lib/liveroom-storage";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import type { JoinRequest } from "../../types";
import type { LiveroomEventType } from "../../types/events";

const POLL_MS = 5000;

const DECISION_EVENTS: ReadonlySet<LiveroomEventType> = new Set<LiveroomEventType>([
  "REQUEST_APPROVED",
  "REQUEST_REJECTED_BY_OWNER",
  "REQUEST_REJECTED_BY_CAPACITY",
  "REQUEST_LOCKED",
]);

interface JoinLobbyProps {
  roomId: string;
  initialRequest: JoinRequest;
  onRetry: () => void;
}

export function JoinLobby({ roomId, initialRequest, onRetry }: JoinLobbyProps) {
  const t = useTranslations("liveroom.lobby");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const router = useRouter();

  const queryClient = useQueryClient();

  const { data, isFetching } = useMyJoinRequest({
    roomId,
    refetchInterval: POLL_MS,
    queryConfig: {
      refetchIntervalInBackground: true,
      initialData: {
        success: true,
        message: "",
        data: initialRequest,
        errors: null,
        timestamp: new Date().toISOString(),
      },
    },
  });

  const request = data?.data ?? initialRequest;
  const terminal = request.state !== "PENDING";

  useEffect(() => {
    liveroomSocket.connect();
    return liveroomSocket.onEvent((event) => {
      if (event.roomId !== roomId || !DECISION_EVENTS.has(event.type)) return;
      void queryClient.invalidateQueries({ queryKey: myJoinRequestKey(roomId) });
    });
  }, [roomId, queryClient]);

  const approvedRef = useRef(false);
  useEffect(() => {
    if (request.state === "APPROVED") approvedRef.current = true;
  }, [request.state]);

  useEffect(
    () => () => {
      if (!approvedRef.current) liveroomSocket.disconnect();
    },
    [],
  );

  useEffect(() => {
    if (terminal && request.state !== "APPROVED") clearIdempotencyKey(roomId);
  }, [terminal, request.state, roomId]);

  const { mutate: cancel, isPending: cancelling } = useCancelJoinRequest({
    mutationConfig: {
      onSuccess: () => {
        clearIdempotencyKey(roomId);
        toast.success(t("cancelled"));
        onRetry();
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const enterRoom = () => router.push(`/liveroom/${roomId}`);

  if (request.state === "APPROVED") {
    return (
      <div className="flex flex-col gap-6">
        <JoinStepIndicator current={4} />
        <AutoJoinCountdown onEnter={enterRoom} />
      </div>
    );
  }

  if (request.state === "LOCKED") {
    return (
      <StateCard
        tone="danger"
        icon={<Ban className="size-8" aria-hidden />}
        title={t("locked")}
        body={t("lockedHint")}
        action={
          <Button variant="outline" className="h-11 md:h-9" onClick={onRetry}>
            {t("backToJoin")}
          </Button>
        }
      />
    );
  }

  if (request.state === "REJECTED_BY_OWNER") {
    return (
      <StateCard
        tone="danger"
        icon={<XCircle className="size-8" aria-hidden />}
        title={t("rejected")}
        body={`${t("rejectedHint")} ${t("attemptsRemaining", {
          count: request.attemptsRemaining,
        })}`}
        action={
          <Button className="h-11 md:h-9" onClick={onRetry}>
            {t("backToJoin")}
          </Button>
        }
      />
    );
  }

  if (request.state === "REJECTED_BY_CAPACITY") {
    return (
      <StateCard
        tone="warning"
        icon={<Clock className="size-8" aria-hidden />}
        title={t("capacityFull")}
        body={t("waitingHint")}
        action={
          <Button className="h-11 md:h-9" onClick={onRetry}>
            {t("backToJoin")}
          </Button>
        }
      />
    );
  }

  if (request.state === "CANCELLED" || request.state === "EXPIRED") {
    return (
      <StateCard
        tone="neutral"
        icon={<Clock className="size-8" aria-hidden />}
        title={t("cancelled")}
        body={t("waitingHint")}
        action={
          <Button className="h-11 md:h-9" onClick={onRetry}>
            {t("backToJoin")}
          </Button>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <JoinStepIndicator current={3} />
      <div className="flex flex-col items-center gap-4 rounded-xl border border-neutral-200 bg-white p-6 text-center md:p-8 dark:border-neutral-800 dark:bg-black">
        <Spinner size="sm" />
        <p aria-live="polite" className="text-lg font-semibold text-black dark:text-white">
          {t("waiting")}
        </p>
        <p className="max-w-sm text-sm text-neutral-500 dark:text-neutral-400">
          {t("waitingHint")}
        </p>
        <Button
          variant="outline"
          className="h-11 md:h-9"
          disabled={cancelling}
          onClick={() => cancel({ roomId, requestId: request.id })}
        >
          {cancelling ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
          {cancelling ? t("cancelling") : t("cancel")}
        </Button>
        {isFetching ? <span className="sr-only">{t("waiting")}</span> : null}
      </div>
    </div>
  );
}

const TONE_STYLES = {
  danger:
    "border-red-300 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200",
  warning:
    "border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200",
  neutral:
    "border-neutral-200 bg-white text-black dark:border-neutral-800 dark:bg-black dark:text-white",
} as const;

function StateCard({
  tone,
  icon,
  title,
  body,
  action,
}: {
  tone: keyof typeof TONE_STYLES;
  icon: React.ReactNode;
  title: string;
  body: string;
  action: React.ReactNode;
}) {
  return (
    <div
      className={`flex flex-col items-center gap-4 rounded-xl border p-6 text-center md:p-8 ${TONE_STYLES[tone]}`}
    >
      {icon}
      <p className="text-lg font-semibold">{title}</p>
      <p className="max-w-sm text-sm opacity-80">{body}</p>
      {action}
    </div>
  );
}