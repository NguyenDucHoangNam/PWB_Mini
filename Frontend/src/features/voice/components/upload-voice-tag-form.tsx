"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { FileAudio, Loader2, UploadCloud, X } from "lucide-react";
import {
  NEU_ERROR_TEXT,
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
  NeuDropzone,
  NeuPanel,
} from "@/components/ui/neu";
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

      {/* Two side-by-side columns left the short name field stranded next to a tall
          dropzone. Stacked, each step reads in the order it is done — name, then file,
          then submit — and the column is capped so neither control stretches the width
          of a desktop panel. */}
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-5">
        <div className="flex flex-col gap-2">
          <label htmlFor="upload-tag-name" className={NEU_LABEL}>
            {t("nameLabel")}
          </label>
          <input
            id="upload-tag-name"
            value={name}
            maxLength={100}
            disabled={isPending}
            onChange={(e) => setName(e.target.value)}
            className={`${NEU_INPUT} h-12`}
          />
        </div>

        <div className="flex flex-col gap-2">
          <label htmlFor="upload-tag-file" className={NEU_LABEL}>
            {t("fileLabel")}
          </label>

          {!file ? (
            <NeuDropzone
              inputRef={fileInputRef}
              disabled={isPending}
              onFiles={handleFileChange}
              className="min-h-44 flex-none"
            >
              {({ isDragging }) => (
                <>
                  <span
                    className={`grid size-12 place-items-center rounded-2xl border-none ${
                      isDragging
                        ? "bg-indigo-600 text-white dark:bg-indigo-500"
                        : "neu-raised-sm text-indigo-600 dark:text-indigo-400"
                    }`}
                  >
                    <UploadCloud className="size-5" aria-hidden="true" />
                  </span>
                  <span className={`text-sm font-bold ${NEU_TEXT}`}>{t("dropzoneTitle")}</span>
                  <span className={`text-xs ${NEU_TEXT_MUTED}`}>
                    {t("dropzoneHint", { max: VOICE_TAG_MAX_DURATION_SECONDS })}
                  </span>
                </>
              )}
            </NeuDropzone>
          ) : (
            <NeuPanel tone="pressed" className="flex flex-col gap-4 rounded-2xl p-4">
              <div className="flex items-center gap-3">
                <span className="neu-raised-sm grid size-11 shrink-0 place-items-center rounded-2xl border-none text-indigo-600 dark:text-indigo-400">
                  <FileAudio className="size-5" aria-hidden="true" />
                </span>
                <div className="flex min-w-0 flex-col gap-0.5">
                  <span className={`truncate text-sm font-bold ${NEU_TEXT}`}>{file.name}</span>
                  <span
                    className={`text-xs font-medium ${tooLong ? "text-rose-700 dark:text-rose-400" : NEU_TEXT_MUTED}`}
                  >
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
                <NeuButton
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  className="ml-auto shrink-0"
                  disabled={isPending}
                  onClick={clearFile}
                  aria-label={tActions("cancel")}
                >
                  <X className="size-4" aria-hidden="true" />
                </NeuButton>
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
            </NeuPanel>
          )}

          {clientError && (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {clientError}
            </p>
          )}
        </div>

        {phase && <UploadProgress phase={phase} percent={percent} />}

        <div className="flex justify-end gap-3">
          {onCancel && (
            <NeuButton type="button" onClick={onCancel} disabled={isPending}>
              {tActions("back")}
            </NeuButton>
          )}
          <NeuButton
            type="submit"
            variant="primary"
            disabled={!canSubmit || isPending}
            className="min-w-36"
          >
            {isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {t("submitButton")}
          </NeuButton>
        </div>
      </div>
    </form>
  );
}
