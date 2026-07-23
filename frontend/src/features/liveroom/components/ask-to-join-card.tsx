"use client";

import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { Mic, MicOff, Video, VideoOff } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useCreateJoinRequest } from "../api/join-requests";
import {
  askToJoinFormSchema,
  type AskToJoinFormValues,
} from "../schemas/room-schema";
import { useMediaDevices } from "../hooks/use-media-devices";
import { applyTrackMutedFlag } from "../stores/use-live-room-media-store";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import type { LiveRoomJoinRequest } from "../types";

interface AskToJoinCardProps {
  roomCode: string;
  isActive: boolean;
  onSent: (request: LiveRoomJoinRequest) => void;
}

export function AskToJoinCard({ roomCode, isActive, onSent }: AskToJoinCardProps) {
  const t = useTranslations("liveroom.askToJoin");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tValidation = useTranslations("validation");
  const tParticipant = useTranslations("liveroom.participant");

  const username = useAuthStore((state) => state.user?.username ?? null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AskToJoinFormValues>({
    resolver: zodResolver(askToJoinFormSchema),
    defaultValues: {
      displayName: username ?? "",
      message: "",
    },
  });

  const devices = useMediaDevices();
  const previewVideoRef = useRef<HTMLVideoElement | null>(null);
  const [micPreviewMuted, setMicPreviewMuted] = useState(false);
  const [cameraPreviewOff, setCameraPreviewOff] = useState(false);
  const [previewStarted, setPreviewStarted] = useState(false);

  const handleStartPreview = async () => {
    try {
      await devices.start();
      setPreviewStarted(true);
    } catch (err) {
      toast.error(
        resolveLiveroomErrorMessage(err as Error, tErrors, tCommon),
      );
    }
  };

  useEffect(() => {
    if (!previewStarted) return;
    const video = previewVideoRef.current;
    if (!video) return;
    if (cameraPreviewOff) {
      video.srcObject = null;
    } else {
      video.srcObject = devices.stream;
    }
  }, [devices.stream, previewStarted, cameraPreviewOff]);

  useEffect(() => {
    if (!previewStarted) return;
    applyTrackMutedFlag(devices.stream, "audio", micPreviewMuted);
  }, [micPreviewMuted, devices.stream, previewStarted]);

  useEffect(() => {
    return () => {
      devices.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { mutate: createJoinRequest, isPending } = useCreateJoinRequest({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success && response.data) {
          toast.success(t("success"));
          devices.stop();
          onSent(response.data);
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
    const payload: { displayName?: string; message?: string } = {};
    const trimmedName = values.displayName?.trim();
    if (trimmedName) payload.displayName = trimmedName;
    const trimmedMessage = values.message?.trim();
    if (trimmedMessage) payload.message = trimmedMessage;
    createJoinRequest({ roomCode, data: payload });
  });

  return (
    <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-neutral-900">
      <div className="flex flex-col gap-1">
        <h2 className="text-base font-semibold text-black dark:text-white">
          {t("titleLabel")}
        </h2>
        <p className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("subtitle")}
        </p>
      </div>

      {previewStarted ? (
        <div className="flex flex-col gap-2">
          <div className="relative aspect-video w-full overflow-hidden rounded-lg bg-neutral-950">
            {cameraPreviewOff ? (
              <div className="flex h-full w-full items-center justify-center">
                <VideoOff className="size-10 text-neutral-500" />
              </div>
            ) : (
              <video
                ref={previewVideoRef}
                autoPlay
                playsInline
                muted
                className="h-full w-full object-cover"
              />
            )}
          </div>
          <div className="flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={() => setMicPreviewMuted((v) => !v)}
              aria-label={micPreviewMuted ? tCommon("loading") : tCommon("loading")}
              className={`inline-flex size-10 items-center justify-center rounded-full transition ${
                micPreviewMuted
                  ? "bg-red-500 text-white hover:bg-red-600"
                  : "bg-neutral-700 text-white hover:bg-neutral-600"
              }`}
            >
              {micPreviewMuted ? <MicOff className="size-4" /> : <Mic className="size-4" />}
            </button>
            <button
              type="button"
              onClick={() => setCameraPreviewOff((v) => !v)}
              aria-label={cameraPreviewOff ? tCommon("loading") : tCommon("loading")}
              className={`inline-flex size-10 items-center justify-center rounded-full transition ${
                cameraPreviewOff
                  ? "bg-red-500 text-white hover:bg-red-600"
                  : "bg-neutral-700 text-white hover:bg-neutral-600"
              }`}
            >
              {cameraPreviewOff ? <VideoOff className="size-4" /> : <Video className="size-4" />}
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={handleStartPreview}
          className="flex items-center justify-center gap-2 rounded-lg border border-dashed border-neutral-300 px-4 py-3 text-sm font-medium text-neutral-700 transition hover:bg-neutral-50 dark:border-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-800"
        >
          <Video className="size-4" />
          {t("startPreview")}
        </button>
      )}

      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="flex flex-col gap-2">
          <Label htmlFor="ask-display-name">{t("displayNameLabel")}</Label>
          <Input
            id="ask-display-name"
            {...register("displayName")}
            maxLength={100}
            placeholder={tParticipant("displayNamePlaceholder")}
            disabled={!isActive || isPending}
          />
          {errors.displayName && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {tValidation(errors.displayName.message as never)}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="ask-message">{t("messageLabel")}</Label>
          <textarea
            id="ask-message"
            {...register("message")}
            maxLength={500}
            rows={3}
            placeholder={t("messagePlaceholder")}
            disabled={!isActive || isPending}
            className="min-h-[80px] w-full rounded-lg border border-neutral-200 bg-transparent px-2.5 py-1.5 text-sm outline-none placeholder:text-neutral-400 focus:border-black focus:ring-1 focus:ring-black/30 dark:border-neutral-800 dark:bg-neutral-950 dark:text-white"
          />
          {errors.message && (
            <p className="text-xs text-red-600 dark:text-red-400">
              {tValidation(errors.message.message as never)}
            </p>
          )}
        </div>

        {!isActive && (
          <p className="text-xs text-neutral-500 dark:text-neutral-400">
            {tCommon("error")}
          </p>
        )}

        <Button type="submit" disabled={isPending || !isActive}>
          {isPending ? (
            <span className="flex items-center gap-2">
              <Spinner size="sm" />
              {t("submitting")}
            </span>
          ) : (
            t("submitBtn")
          )}
        </Button>
      </form>
    </div>
  );
}