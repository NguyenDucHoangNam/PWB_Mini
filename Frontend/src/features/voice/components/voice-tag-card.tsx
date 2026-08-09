"use client";

import { useState, useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Play, Pause, Volume2, VolumeX, Pencil, Trash2, Check, X } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import {
  NEU_DANGER_TEXT,
  NEU_FOCUS,
  NEU_INPUT,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuBadge,
  NeuButton,
  NeuPanel,
} from "@/components/ui/neu";
import { asApiError } from "@/lib/api-client";
import type { VoiceTag } from "../types";
import { useVoiceTagAudioUrl, useUpdateVoiceTag } from "../api/voice-tags";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { formatDuration } from "../lib/format-audio";
import { VoiceTagDeleteDialog } from "./voice-tag-delete-dialog";

import { LanguageFlagIcon } from "./language-flag";

const SEEK_STEP_SECONDS = 1;

interface VoiceTagCardProps {
  voiceTag: VoiceTag;
}

export function VoiceTagCard({ voiceTag }: VoiceTagCardProps) {
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(voiceTag.name);
  const inputRef = useRef<HTMLInputElement>(null);

  const { mutate: updateTag, isPending } = useUpdateVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(tCommon("save"));
          setIsEditing(false);
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [isEditing]);

  const startEdit = () => {
    setEditValue(voiceTag.name);
    setIsEditing(true);
  };

  const cancelEdit = () => {
    setEditValue(voiceTag.name);
    setIsEditing(false);
  };

  const saveEdit = () => {
    const trimmed = editValue.trim();
    if (!trimmed || isPending) return;
    if (trimmed === voiceTag.name) {
      setIsEditing(false);
      return;
    }
    updateTag({ voiceTagId: voiceTag.id, data: { name: trimmed } });
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      saveEdit();
    } else if (e.key === "Escape") {
      cancelEdit();
    }
  };

  return (
    <NeuPanel as="article" tone="tile" className="group flex h-full flex-col gap-5 p-5">
      <div className="flex items-start justify-between gap-2">
        {isEditing ? (
          <div className="flex min-w-0 flex-1 items-center gap-1.5">
            <input
              ref={inputRef}
              type="text"
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              onKeyDown={handleKeyDown}
              onBlur={cancelEdit}
              maxLength={100}
              disabled={isPending}
              aria-label={tActions("edit")}
              className={`${NEU_INPUT} h-11 min-w-0 flex-1 rounded-xl font-semibold`}
            />
            <NeuButton
              variant="primary"
              size="icon-sm"
              className="size-11 sm:size-10"
              disabled={isPending || !editValue.trim()}
              onMouseDown={(e) => {
                e.preventDefault();
                saveEdit();
              }}
              aria-label={tCommon("save")}
            >
              <Check className="size-4" aria-hidden="true" />
            </NeuButton>
            <NeuButton
              variant="ghost"
              size="icon-sm"
              className="size-11 sm:size-10"
              onMouseDown={(e) => {
                e.preventDefault();
                cancelEdit();
              }}
              aria-label={tCommon("cancel")}
            >
              <X className="size-4" aria-hidden="true" />
            </NeuButton>
          </div>
        ) : (
          <>
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <h3 className={`truncate text-base font-bold tracking-tight ${NEU_TEXT}`}>
                {voiceTag.name}
              </h3>
              <div
                className={`flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs font-medium ${NEU_TEXT_MUTED}`}
              >
                {voiceTag.languageCode && (
                  <NeuBadge tone="muted" className="px-2 py-0.5">
                    <LanguageFlagIcon
                      langCode={voiceTag.languageCode}
                      className="h-3 w-4.5 shrink-0 rounded-xs"
                    />
                    {voiceTag.languageCode}
                  </NeuBadge>
                )}
                <span className="tabular-nums">{formatDuration(voiceTag.durationSeconds)}</span>
              </div>
            </div>

            <div className="hover-reveal flex shrink-0 items-center gap-1">
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className="size-11 sm:size-9"
                onClick={startEdit}
                aria-label={tActions("edit")}
              >
                <Pencil className="size-4" aria-hidden="true" />
              </NeuButton>
              <NeuButton
                variant="ghost"
                size="icon-sm"
                className="size-11 hover:text-rose-700 sm:size-9 dark:hover:text-rose-400"
                onClick={() => setDeleteOpen(true)}
                aria-label={tActions("delete")}
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </NeuButton>
            </div>
          </>
        )}
      </div>

      <div className="mt-auto">
        <VoiceTagPreviewInline voiceTagId={voiceTag.id} />
      </div>

      <VoiceTagDeleteDialog
        voiceTagId={voiceTag.id}
        voiceTagName={voiceTag.name}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
      />
    </NeuPanel>
  );
}

function VoiceTagPreviewInline({ voiceTagId }: { voiceTagId: string }) {
  const t = useTranslations("voice.voiceTags");
  const tPlayer = useTranslations("voice.player");
  const [requested, setRequested] = useState(false);
  const { data, isLoading, error } = useVoiceTagAudioUrl({
    voiceTagId,
    enabled: requested,
  });
  const url = data?.data?.url ?? null;

  if (!requested) {
    return (
      <NeuButton
        onClick={() => setRequested(true)}
        className="h-12 w-full justify-start gap-3 px-3"
      >
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white dark:bg-indigo-500">
          <Play className="ml-0.5 size-3.5 fill-current" aria-hidden="true" />
        </span>
        <span className="text-sm font-semibold">{t("preview")}</span>
      </NeuButton>
    );
  }

  if (isLoading) {
    return (
      <div
        role="status"
        className={`neu-pressed flex h-12 items-center gap-2.5 rounded-2xl border-none px-4 text-sm font-medium ${NEU_TEXT_MUTED}`}
      >
        <Spinner size="sm" />
        {t("preview")}
      </div>
    );
  }

  if (error || !url) {
    return (
      <p
        role="alert"
        className={`neu-pressed flex h-12 items-center rounded-2xl border-none px-4 text-sm font-medium ${NEU_DANGER_TEXT}`}
      >
        {tPlayer("loadError")}
      </p>
    );
  }

  return <VoiceTagCustomPlayer url={url} autoPlay />;
}

function VoiceTagCustomPlayer({ url, autoPlay = false }: { url: string; autoPlay?: boolean }) {
  const tActions = useTranslations("voice.actions");
  const tPlayer = useTranslations("voice.player");
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isMuted, setIsMuted] = useState(false);

  const togglePlay = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play();
    }
  };

  const toggleMute = () => {
    if (!audioRef.current) return;
    audioRef.current.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const seekTo = (seconds: number) => {
    if (!audioRef.current || !duration) return;
    const clamped = Math.min(Math.max(seconds, 0), duration);
    audioRef.current.currentTime = clamped;
    setCurrentTime(clamped);
  };

  const handleSeekClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    seekTo(((e.clientX - rect.left) / rect.width) * duration);
  };

  const handleSeekKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      seekTo(currentTime + SEEK_STEP_SECONDS);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      seekTo(currentTime - SEEK_STEP_SECONDS);
    }
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="neu-pressed flex h-12 items-center gap-2.5 rounded-2xl border-none px-3">
      <audio
        ref={audioRef}
        src={url}
        autoPlay={autoPlay}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={() => audioRef.current && setCurrentTime(audioRef.current.currentTime)}
        onLoadedMetadata={() => audioRef.current && setDuration(audioRef.current.duration)}
        onEnded={() => {
          setIsPlaying(false);
          setCurrentTime(0);
        }}
      />

      <NeuButton
        variant="primary"
        size="icon-sm"
        className="size-8 rounded-full"
        onClick={togglePlay}
        aria-label={isPlaying ? tPlayer("pause") : tActions("play")}
      >
        {isPlaying ? (
          <Pause className="size-3.5 fill-current" aria-hidden="true" />
        ) : (
          <Play className="ml-0.5 size-3.5 fill-current" aria-hidden="true" />
        )}
      </NeuButton>

      <span className={`shrink-0 text-xs font-semibold tabular-nums ${NEU_TEXT_MUTED}`}>
        {formatDuration(currentTime)}
      </span>

      <div
        role="slider"
        tabIndex={0}
        aria-label={tPlayer("seek")}
        aria-valuemin={0}
        aria-valuemax={Math.round(duration)}
        aria-valuenow={Math.round(currentTime)}
        onClick={handleSeekClick}
        onKeyDown={handleSeekKeyDown}
        className={`relative flex h-6 flex-1 cursor-pointer items-center rounded-full ${NEU_FOCUS}`}
      >
        {/* Sunken groove, accent fill: the played portion has to read as colour,
            not as a shadow, to be visible at all. */}
        <div className="neu-pressed-sm h-2 w-full overflow-hidden rounded-full border-none">
          <div
            className="h-full rounded-full bg-indigo-600 dark:bg-indigo-400"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      <span className={`shrink-0 text-xs font-semibold tabular-nums ${NEU_TEXT_MUTED}`}>
        {formatDuration(duration)}
      </span>

      <NeuButton
        variant="ghost"
        size="icon-sm"
        className="size-8 rounded-full"
        onClick={toggleMute}
        aria-label={isMuted ? tPlayer("unmute") : tPlayer("mute")}
      >
        {isMuted ? (
          <VolumeX className="size-3.5" aria-hidden="true" />
        ) : (
          <Volume2 className="size-3.5" aria-hidden="true" />
        )}
      </NeuButton>
    </div>
  );
}
