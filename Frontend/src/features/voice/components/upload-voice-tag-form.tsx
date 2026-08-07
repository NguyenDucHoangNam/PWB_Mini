"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { FileAudio, Loader2, UploadCloud, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { UploadProgress, type UploadPhase } from "@/components/ui/upload-progress";
import { asApiError } from "@/lib/api-client";
import { useCreateUploadedVoiceTag } from "../api/voice-tags";
import { readAudioDuration } from "../lib/read-audio-duration";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { useFileValidation } from "../hooks/use-file-validation";
import { VOICE_TAG_MAX_DURATION_SECONDS } from "../types";

interface UploadVoiceTagFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

function formatSeconds(seconds: number) {
  return `${seconds.toFixed(1)}s`;
}

export function UploadVoiceTagForm({ onCancel, onSuccess }: UploadVoiceTagFormProps) {
  const t = useTranslations("voice.voiceTags.upload");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const router = useRouter();

  const { validateAudioFile } = useFileValidation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const autoFilledName = useRef<string | null>(null);

  const [name, setName] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [duration, setDuration] = useState(0);
  const [isReadingDuration, setIsReadingDuration] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [phase, setPhase] = useState<UploadPhase | null>(null);
  const [percent, setPercent] = useState(0);

  // An object URL keeps its blob alive until revoked, so the previous preview is released whenever a
  // new file replaces it and when the form unmounts.
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );

  const clearFile = useCallback(() => {
    setFile(null);
    setDuration(0);
    setPreviewUrl(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }, []);

  const handleFileChange = useCallback(
    async (selected: File | null) => {
      setClientError(null);
      if (!selected) {
        clearFile();
        return;
      }

      const formatError = validateAudioFile(selected);
      if (formatError) {
        setClientError(formatError);
        clearFile();
        return;
      }

      setFile(selected);
      setPreviewUrl(URL.createObjectURL(selected));

      // Swapping files should re-derive the name, but only while the user has not written one of their
      // own — hence tracking what was auto-filled rather than just testing for emptiness. The previous
      // value is read into a local first: the updater runs after this function returns, by which point
      // the ref would already hold the new name and every comparison would fail.
      const derived = selected.name.replace(/\.[^/.]+$/, "");
      const previouslyDerived = autoFilledName.current;
      autoFilledName.current = derived;
      setName((current) => (!current.trim() || current === previouslyDerived ? derived : current));

      // The server measures this again with ffprobe and is the authority; checking here only saves the
      // user an upload that was always going to be refused.
      setIsReadingDuration(true);
      const seconds = await readAudioDuration(selected);
      setIsReadingDuration(false);
      setDuration(seconds);

      if (seconds <= 0) {
        setClientError(tErrors("invalidDuration"));
      } else if (seconds > VOICE_TAG_MAX_DURATION_SECONDS) {
        setClientError(t("tooLong", { max: VOICE_TAG_MAX_DURATION_SECONDS }));
      }
    },
    [validateAudioFile, clearFile, t, tErrors],
  );

  const { mutate: createTag, isPending } = useCreateUploadedVoiceTag({
    mutationConfig: {
      onSettled: () => {
        setPhase(null);
        setPercent(0);
      },
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("createSuccess"));
          onSuccess?.();
          router.push("/dashboard/voice-tags");
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const tooLong = duration > VOICE_TAG_MAX_DURATION_SECONDS;
  const canSubmit =
    Boolean(file) && Boolean(name.trim()) && duration > 0 && !tooLong && !isReadingDuration;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      setClientError(tErrors("fileRequired"));
      return;
    }
    if (!name.trim()) {
      setClientError(tValidation("name.required"));
      return;
    }
    setPhase("uploading");
    setPercent(0);
    createTag({
      name: name.trim(),
      file,
      // The clip is small, so the bytes are gone in a blink; the wait the user actually sees is the
      // server probing and storing it, which is what `finalizing` covers.
      onProgress: (value) => {
        setPercent(value);
        if (value >= 100) setPhase("finalizing");
      },
    });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <input
        ref={fileInputRef}
        id="upload-tag-file"
        type="file"
        accept="audio/mpeg,audio/wav,audio/flac,.mp3,.wav,.flac"
        className="sr-only"
        disabled={isPending}
        onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
      />

      <div className="grid gap-5 lg:grid-cols-2 lg:gap-6">
        <div className="flex flex-col gap-2">
          <Label htmlFor="upload-tag-name">{t("nameLabel")}</Label>
          <Input
            id="upload-tag-name"
            value={name}
            maxLength={100}
            disabled={isPending}
            onChange={(e) => setName(e.target.value)}
            className="h-10"
          />
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="upload-tag-file">{t("fileLabel")}</Label>

          {!file ? (
            <button
              type="button"
              disabled={isPending}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={(e) => {
                e.preventDefault();
                setIsDragging(false);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragging(false);
                handleFileChange(e.dataTransfer.files?.[0] ?? null);
              }}
              className={`key-press flex flex-1 flex-col items-center justify-center gap-2 rounded-xl border border-dashed p-6 text-center beat-16th transition-colors ease-hammer ${
                isDragging
                  ? "border-foreground bg-muted"
                  : "border-border hover:border-foreground/40 hover:bg-muted/50"
              }`}
            >
              <UploadCloud className="size-6 text-muted-foreground" aria-hidden="true" />
              <span className="text-sm font-medium text-foreground">{t("dropzoneTitle")}</span>
              <span className="text-xs text-muted-foreground">
                {t("dropzoneHint", { max: VOICE_TAG_MAX_DURATION_SECONDS })}
              </span>
            </button>
          ) : (
            <div className="flex flex-1 flex-col gap-3 rounded-xl border border-border bg-muted/40 p-4">
              <div className="flex items-center gap-3">
                <FileAudio className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <div className="flex min-w-0 flex-col">
                  <span className="truncate text-sm font-medium text-foreground">{file.name}</span>
                  <span className={`text-xs ${tooLong ? "text-destructive" : "text-muted-foreground"}`}>
                    {isReadingDuration
                      ? t("readingDuration")
                      : duration > 0
                        ? t("durationOf", {
                            duration: formatSeconds(duration),
                            max: VOICE_TAG_MAX_DURATION_SECONDS,
                          })
                        : t("durationUnknown")}
                  </span>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="ml-auto size-9 shrink-0 sm:size-8"
                  disabled={isPending}
                  onClick={clearFile}
                  aria-label={tActions("cancel")}
                >
                  <X className="size-4" aria-hidden="true" />
                </Button>
              </div>

              {previewUrl && (
                <audio
                  controls
                  preload="metadata"
                  src={previewUrl}
                  className="w-full"
                  aria-label={file.name}
                />
              )}
            </div>
          )}

          {clientError && (
            <p role="alert" className="text-xs text-destructive">
              {clientError}
            </p>
          )}
        </div>
      </div>

      {phase && <UploadProgress phase={phase} percent={percent} />}

      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="ghost" size="lg" onClick={onCancel} disabled={isPending}>
            {tActions("back")}
          </Button>
        )}
        <Button
          type="submit"
          size="lg"
          disabled={!canSubmit || isPending}
          className="h-11 min-w-36 sm:h-9"
        >
          {isPending && <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />}
          {t("submitButton")}
        </Button>
      </div>
    </form>
  );
}
