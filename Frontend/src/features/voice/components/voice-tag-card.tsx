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
import { VoiceTagDeleteDialog } from "./voice-tag-delete-dialog";

import { LanguageFlagIcon } from "./language-flag";

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
    <div className="group relative flex flex-col justify-between gap-3.5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2.5 min-w-0 flex-1">
          {isEditing ? (
            <div className="flex items-center gap-1.5 min-w-0 flex-1">
              <input
                ref={inputRef}
                type="text"
                value={editValue}
                onChange={(e) => setEditValue(e.target.value)}
                onKeyDown={handleKeyDown}
                onBlur={cancelEdit}
                maxLength={100}
                disabled={isPending}
                className="min-w-0 flex-1 rounded border border-neutral-400 bg-white px-2 py-0.5 text-base font-bold tracking-tight text-neutral-900 outline-none focus:border-black dark:border-neutral-600 dark:bg-neutral-900 dark:text-neutral-50 dark:focus:border-white"
              />
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  saveEdit();
                }}
                disabled={isPending || !editValue.trim()}
                className="flex size-7 shrink-0 items-center justify-center rounded border border-neutral-300 bg-neutral-100 text-neutral-800 hover:bg-neutral-200 dark:border-neutral-700 dark:bg-neutral-800 dark:text-neutral-200 dark:hover:bg-neutral-700"
                aria-label={tCommon("save")}
              >
                <Check className="size-4" />
              </button>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  cancelEdit();
                }}
                className="flex size-7 shrink-0 items-center justify-center rounded border border-neutral-200 text-neutral-600 hover:bg-neutral-100 dark:border-neutral-800 dark:text-neutral-400 dark:hover:bg-neutral-800"
                aria-label={tCommon("cancel")}
              >
                <X className="size-4" />
              </button>
            </div>
          ) : (
            <>
              <h3 className="truncate text-base font-bold tracking-tight text-neutral-900 dark:text-neutral-50 group-hover:underline decoration-neutral-400 underline-offset-4">
                {voiceTag.name}
              </h3>
              {voiceTag.languageCode && (
                <span className="inline-flex shrink-0 items-center gap-1.5 rounded bg-neutral-100 px-2 py-0.5 text-[11px] font-mono font-semibold text-neutral-800 border border-neutral-300 dark:bg-neutral-900 dark:text-neutral-200 dark:border-neutral-700 uppercase tracking-wide">
                  <LanguageFlagIcon langCode={voiceTag.languageCode} className="h-3 w-[18px] rounded-[1px] shrink-0" />
                  {voiceTag.languageCode}
                </span>
              )}
            </>
          )}
        </div>

        {!isEditing && (
          <div className="flex shrink-0 items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="size-8 rounded text-neutral-500 hover:bg-neutral-200 hover:text-black dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
              onClick={startEdit}
              title={tActions("edit")}
            >
              <Pencil className="size-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="size-8 rounded text-neutral-500 hover:bg-neutral-200 hover:text-black dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
              onClick={() => setDeleteOpen(true)}
              title={tActions("delete")}
            >
              <Trash2 className="size-4" />
            </Button>
          </div>
        )}
      </div>

      <VoiceTagPreviewInline voiceTagId={voiceTag.id} />

      <VoiceTagDeleteDialog
        voiceTagId={voiceTag.id}
        voiceTagName={voiceTag.name}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
      />
    </div>
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
      <button
        type="button"
        onClick={() => setRequested(true)}
        className="flex items-center gap-3 rounded-lg border border-neutral-300 bg-neutral-100 px-3.5 py-2.5 text-xs font-semibold text-neutral-800 transition-all hover:bg-neutral-200 active:translate-y-[1px] dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-200 dark:hover:bg-neutral-800"
      >
        <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-black text-white dark:bg-white dark:text-black shadow-xs">
          <Play className="ml-0.5 size-3.5 fill-current" />
        </span>
        <span className="font-mono tracking-wide">{t("preview")}</span>
      </button>
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-neutral-100 px-3 py-2.5 text-xs font-mono text-neutral-500 dark:bg-neutral-900">
        <Spinner size="sm" />
        {t("preview")}
      </div>
    );
  }

  if (error || !url) {
    return (
      <div className="rounded-lg border border-dashed border-neutral-400 bg-neutral-100 px-3 py-2.5 text-xs font-mono text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300">
        {tPlayer("loadError")}
      </div>
    );
  }

  return <VoiceTagCustomPlayer url={url} autoPlay />;
}

function VoiceTagCustomPlayer({ url, autoPlay = false }: { url: string; autoPlay?: boolean }) {
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

  const handleTimeUpdate = () => {
    if (!audioRef.current) return;
    setCurrentTime(audioRef.current.currentTime);
  };

  const handleLoadedMetadata = () => {
    if (!audioRef.current) return;
    setDuration(audioRef.current.duration);
  };

  const handleEnded = () => {
    setIsPlaying(false);
    setCurrentTime(0);
  };

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!audioRef.current || !duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const percentage = clickX / rect.width;
    const newTime = percentage * duration;
    audioRef.current.currentTime = newTime;
    setCurrentTime(newTime);
  };

  const formatTime = (timeInSec: number) => {
    if (!Number.isFinite(timeInSec)) return "0:00";
    const mins = Math.floor(timeInSec / 60);
    const secs = Math.floor(timeInSec % 60);
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="flex items-center gap-3 rounded-lg bg-neutral-100/90 px-3.5 py-2.5 dark:bg-neutral-900/90 border border-neutral-300 dark:border-neutral-800 shadow-xs">
      <audio
        ref={audioRef}
        src={url}
        autoPlay={autoPlay}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={handleLoadedMetadata}
        onEnded={handleEnded}
      />
      <button
        type="button"
        onClick={togglePlay}
        className="flex size-8 shrink-0 items-center justify-center rounded-md bg-black text-white hover:bg-neutral-800 active:translate-y-[1px] dark:bg-white dark:text-black dark:hover:bg-neutral-200 transition-all"
        aria-label={isPlaying ? "Pause" : "Play"}
      >
        {isPlaying ? (
          <Pause className="size-3.5 fill-current" />
        ) : (
          <Play className="size-3.5 fill-current ml-0.5" />
        )}
      </button>

      {isPlaying && (
        <span className="hidden sm:flex items-end gap-0.5 h-3.5 w-3 shrink-0" aria-hidden="true">
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_100ms] h-full rounded-full" />
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_300ms] h-2/3 rounded-full" />
          <span className="w-0.5 bg-neutral-800 dark:bg-neutral-200 animate-[bounce_1s_infinite_200ms] h-4/5 rounded-full" />
        </span>
      )}

      <div className="flex flex-1 items-center gap-2 min-w-0">
        <span className="text-[11px] font-mono text-neutral-600 dark:text-neutral-400 shrink-0 min-w-[28px]">
          {formatTime(currentTime)}
        </span>
        <div
          className="relative h-2 flex-1 overflow-hidden rounded-full bg-neutral-300 dark:bg-neutral-800 cursor-pointer group"
          onClick={handleSeek}
        >
          <div
            className="h-full rounded-full bg-black dark:bg-white transition-all"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
        <span className="text-[11px] font-mono text-neutral-500 shrink-0 min-w-[28px]">
          {formatTime(duration)}
        </span>
      </div>

      <button
        type="button"
        onClick={toggleMute}
        className="text-neutral-500 hover:text-black dark:text-neutral-400 dark:hover:text-white transition-colors shrink-0 p-1"
        aria-label={isMuted ? "Unmute" : "Muted"}
      >
        {isMuted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
      </button>
    </div>
  );
}