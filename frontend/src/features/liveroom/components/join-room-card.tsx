"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useJoinRoom, useLeaveRoom } from "../api/participants";
import { joinRoomFormSchema, type JoinRoomFormValues } from "../schemas/room-schema";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoomJoinResponse } from "../types";

interface JoinRoomCardProps {
  roomCode: string;
  isActive: boolean;
  isHost: boolean;
  currentParticipantId?: string | null;
}

export function JoinRoomCard({
  roomCode,
  isActive,
  isHost,
  currentParticipantId,
}: JoinRoomCardProps) {
  const router = useRouter();
  const t = useTranslations("liveroom.join");
  const tParticipant = useTranslations("liveroom.participant");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");

  const username = useAuthStore((state) => state.user?.username ?? null);

  const { register, handleSubmit, formState: { errors } } = useForm<JoinRoomFormValues>({
    resolver: zodResolver(joinRoomFormSchema),
    defaultValues: { displayName: "" },
  });

  const [lastJoin, setLastJoin] = useState<LiveRoomJoinResponse | null>(null);

  const { mutate: joinRoom, isPending: isJoining } = useJoinRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success && response.data) {
          setLastJoin(response.data);
          toast.success(t("joinSuccess"));
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: leaveRoom, isPending: isLeaving } = useLeaveRoom({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          setLastJoin(null);
          toast.success(t("leaveSuccess"));
          router.refresh();
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const onSubmit = handleSubmit((values) => {
    joinRoom({
      roomCode,
      data: {
        displayName: values.displayName?.trim() || undefined,
      },
    });
  });

  const handleLeave = () => {
    leaveRoom({ roomCode });
  };

  const joined = Boolean(lastJoin?.participantId) || Boolean(currentParticipantId);
  const disabled = !isActive || isHost;

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
      {joined ? (
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2 text-sm text-green-700 dark:text-green-300">
            <span className="inline-flex size-2 rounded-full bg-green-500 animate-pulse" />
            <span className="font-semibold">
              {lastJoin?.displayName ?? currentParticipantId ?? username ?? t("joinSuccess")}
            </span>
          </div>
          <Button
            variant="destructive"
            onClick={handleLeave}
            disabled={isLeaving}
          >
            {isLeaving ? (
              <span className="flex items-center gap-2">
                <Spinner size="sm" />
                {t("leaving")}
              </span>
            ) : (
              t("leaveBtn")
            )}
          </Button>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-3">
          <div className="flex flex-col gap-2">
            <Label htmlFor="join-display-name">{tParticipant("displayNameLabel")}</Label>
            <Input
              id="join-display-name"
              {...register("displayName")}
              maxLength={100}
              placeholder={tParticipant("displayNamePlaceholder")}
              disabled={disabled}
            />
            {errors.displayName && (
              <p className="text-xs text-red-600 dark:text-red-400">
                {tValidation(errors.displayName.message as never)}
              </p>
            )}
          </div>

          {disabled && (
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              {isHost ? t("joinDisabled") : !isActive ? t("ended") : ""}
            </p>
          )}

          <Button type="submit" disabled={isJoining || disabled}>
            {isJoining ? (
              <span className="flex items-center gap-2">
                <Spinner size="sm" />
                {t("joining")}
              </span>
            ) : (
              t("joinBtn")
            )}
          </Button>
        </form>
      )}
    </div>
  );
}
