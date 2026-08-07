"use client";

import { useState, useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Play, Pause, Volume2, VolumeX, Pencil, Trash2, Check, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
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
    <article className="group flex h-full flex-col gap-4 rounded-xl border border-border bg-card p-4 beat-16th transition-colors ease-hammer hover:border-foreground/20">
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
              className="h-9 min-w-0 flex-1 rounded-lg border border-input bg-background px-2.5 text-sm font-semibold text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
            />
            <Button
              variant="secondary"
              size="icon"
              className="size-9 shrink-0"
              disabled={isPending || !editValue.trim()}
              onMouseDown={(e) => {
                e.preventDefault();
                saveEdit();
              }}
              aria-label={tCommon("save")}
            >
              <Check className="size-4" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="size-9 shrink-0"
              onMouseDown={(e) => {
                e.preventDefault();
                cancelEdit();
              }}
              aria-label={tCommon("cancel")}
            >
              <X className="size-4" aria-hidden="true" />
            </Button>
          </div>
        ) : (
          <>
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
              <h3 className="truncate text-base font-semibold tracking-tight text-foreground">
                {voiceTag.name}
              </h3>
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                {voiceTag.languageCode && (
                  <span className="inline-flex items-center gap-1.5 rounded-md border border-border px-1.5 py-0.5">
                    <LanguageFlagIcon
                      langCode={voiceTag.languageCode}
                      className="h-3 w-4.5 shrink-0 rounded-xs"
                    />
                    {voiceTag.languageCode}
                  </span>
                )}
                <span className="tabular-nums">{formatDuration(voiceTag.durationSeconds)}</span>
              </div>
            </div>

            <div className="hover-reveal flex shrink-0 items-center gap-0.5">
              <Button
                variant="ghost"
                size="icon"
                className="size-11 text-muted-foreground hover:text-foreground sm:size-8"
                onClick={startEdit}
                aria-label={tActions("edit")}
              >
                <Pencil className="size-4 sm:size-3.5" aria-hidden="true" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="size-11 text-muted-foreground hover:text-destructive sm:size-8"
                onClick={() => setDeleteOpen(true)}
                aria-label={tActions("delete")}
              >
                <Trash2 className="size-4 sm:size-3.5" aria-hidden="true" />
              </Button>
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
    </article>
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
      <Button
        variant="outline"
        size="lg"
        onClick={() => setRequested(true)}
        className="h-11 w-full justify-start gap-3 px-3"
      >
        <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Play className="ml-0.5 size-3 fill-current" aria-hidden="true" />
        </span>
        <span className="text-sm font-medium">{t("preview")}</span>
      </Button>
    );
  }

  if (isLoading) {
    return (
      <div
        role="status"
        className="flex h-11 items-center gap-2 rounded-lg border border-border bg-secondary px-3 text-sm text-muted-foreground"
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
        className="flex h-11 items-center rounded-lg border border-dashed border-border px-3 text-sm text-muted-foreground"
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
    <div className="flex h-11 items-center gap-2.5 rounded-lg border border-border bg-secondary px-2.5">
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

      <Button
        size="icon"
        className="size-7 shrink-0 rounded-full"
        onClick={togglePlay}
        aria-label={isPlaying ? tPlayer("pause") : tActions("play")}
      >
        {isPlaying ? (
          <Pause className="size-3 fill-current" aria-hidden="true" />
        ) : (
          <Play className="ml-0.5 size-3 fill-current" aria-hidden="true" />
        )}
      </Button>

      <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
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
        className="relative flex h-6 flex-1 cursor-pointer items-center rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      >
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-border">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
        {formatDuration(duration)}
      </span>

      <Button
        variant="ghost"
        size="icon"
        className="size-7 shrink-0 text-muted-foreground hover:text-foreground"
        onClick={toggleMute}
        aria-label={isMuted ? tPlayer("unmute") : tPlayer("mute")}
      >
        {isMuted ? (
          <VolumeX className="size-3.5" aria-hidden="true" />
        ) : (
          <Volume2 className="size-3.5" aria-hidden="true" />
        )}
      </Button>
    </div>
  );
}
