"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useForm, useWatch, type UseFormRegisterReturn } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Info, UploadCloud, FileAudio, Loader2, RefreshCcw, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { UploadProgress } from "@/components/ui/upload-progress";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { readAudioDuration } from "../lib/read-audio-duration";
import { formatDuration } from "../lib/format-audio";
import { getPresignedUploadUrl, createSong, getSong, SONGS_KEY } from "../api/songs";
import { useListVoiceTags } from "../api/voice-tags";
import { VoiceTagPicker } from "./voice-tag-picker";
import { useFileValidation } from "../hooks/use-file-validation";
import { uploadSongFormSchema, type UploadSongFormValues, type UploadSongFormInput } from "../schemas/song-schema";
import type { CreateSongRequest, SongStatus } from "../types";

type UploadStep = "idle" | "preparing" | "uploading" | "creating" | "merging";

/**
 * How often the form asks whether the merge has landed, and how long it is willing to wait.
 *
 * The cap is not a failure: the merge keeps running server-side. It is there so a queue backed up
 * behind other songs cannot pin the user to this screen indefinitely — past it they are handed to the
 * song's own page, which polls for the same thing.
 */
const MERGE_POLL_INTERVAL_MS = 2000;
const MERGE_WAIT_LIMIT_MS = 5 * 60 * 1000;

const TIMELINE_TICKS = 50;

interface SongUploadFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

// A static bar pattern rather than a real waveform: decoding the file just to draw a preview isn't
// worth it, and this reads as "audio" at a glance without pretending to reflect the actual track.
const FILE_WAVE_HEIGHTS = [35, 70, 45, 90, 60, 100, 50, 80, 40, 65, 95, 55, 30, 75, 45, 85, 40, 60];

function FileWaveform({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-end gap-0.5 ${className}`} aria-hidden="true">
      {FILE_WAVE_HEIGHTS.map((h, i) => (
        <span key={i} className="w-0.5 shrink-0 rounded-full bg-foreground/25" style={{ height: `${h}%` }} />
      ))}
    </div>
  );
}

function SelectedFileSummary({
  fileName,
  sizeLabel,
  durationSeconds,
}: {
  fileName: string;
  sizeLabel: string;
  durationSeconds: number;
}) {
  const extension = fileName.split(".").pop()?.toUpperCase() ?? "";
  return (
    <div className="flex min-w-0 items-center gap-3.5">
      <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
        <FileAudio className="size-5" aria-hidden="true" />
      </span>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="truncate text-sm font-semibold text-foreground">{fileName}</p>
        <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
          <span>{sizeLabel}</span>
          <span aria-hidden="true">·</span>
          <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold tracking-wide text-foreground/70">
            {extension}
          </span>
          {durationSeconds > 0 && (
            <>
              <span aria-hidden="true">·</span>
              <span className="tabular-nums">{formatDuration(durationSeconds)}</span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

interface SliderFieldProps {
  id: string;
  label: string;
  hint: string;
  unit: string;
  min: number;
  max: number;
  value: number;
  disabled: boolean;
  registration: UseFormRegisterReturn;
  onSlide: (value: number) => void;
}

function SliderField({
  id,
  label,
  hint,
  unit,
  min,
  max,
  value,
  disabled,
  registration,
  onSlide,
}: SliderFieldProps) {
  return (
    <div className="flex flex-col gap-2.5 rounded-xl border border-border p-4">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id} className="text-xs font-medium text-muted-foreground">
          {label}
        </Label>
        <div className="flex items-center gap-1">
          <Input
            id={id}
            type="number"
            min={min}
            max={max}
            disabled={disabled}
            className="h-7 w-16 px-1.5 text-right text-xs tabular-nums"
            {...registration}
          />
          <span className="text-xs text-muted-foreground">{unit}</span>
        </div>
      </div>
      <input
        type="range"
        aria-label={label}
        min={min}
        max={max}
        value={value}
        disabled={disabled}
        onChange={(e) => onSlide(Number(e.target.value))}
        className="h-1.5 w-full cursor-pointer rounded-lg bg-border accent-primary"
      />
      <p className="text-xs leading-tight text-muted-foreground">{hint}</p>
    </div>
  );
}

export function SongUploadForm({ onCancel, onSuccess }: SongUploadFormProps) {
  const t = useTranslations("voice.songs.form");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const tUpload = useTranslations("upload");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const xhrRef = useRef<XMLHttpRequest | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileDuration, setFileDuration] = useState<number>(0);
  const [isDragging, setIsDragging] = useState(false);
  const [clientError, setClientError] = useState<string | null>(null);
  const [uploadStep, setUploadStep] = useState<UploadStep>("idle");
  const [uploadProgress, setUploadProgress] = useState(0);
  const [mergingSongId, setMergingSongId] = useState<string | null>(null);
  // Set when the user walks away from the wait, so the poll loop stops asking.
  const abandonedRef = useRef(false);
  const router = useRouter();
  const queryClient = useQueryClient();

  const { validateAudioFile } = useFileValidation();
  const { data: voiceTagsRes } = useListVoiceTags({ page: 0, size: 50 });
  const voiceTags = voiceTagsRes?.data?.content ?? [];

  const isBusy = uploadStep !== "idle";

  const {
    register,
    handleSubmit,
    control,
    getValues,
    setValue,
    formState: { errors },
  } = useForm<UploadSongFormInput, unknown, UploadSongFormValues>({
    resolver: zodResolver(uploadSongFormSchema),
    defaultValues: {
      title: "",
      attachVoiceTag: false,
      voiceTagId: "",
      intervalSeconds: 30,
      volumePercentage: 80,
      duckingPercentage: 50,
      startOffsetSeconds: 0,
    },
  });

  const attachVoiceTag = useWatch({ control, name: "attachVoiceTag" });
  const rawInterval = useWatch({ control, name: "intervalSeconds" });
  const rawVolume = useWatch({ control, name: "volumePercentage" });
  const rawDucking = useWatch({ control, name: "duckingPercentage" });
  const rawOffset = useWatch({ control, name: "startOffsetSeconds" });

  const intervalSeconds = typeof rawInterval === "number" ? rawInterval : Number(rawInterval) || 30;
  const volumePercentage = typeof rawVolume === "number" ? rawVolume : Number(rawVolume) || 80;
  const duckingPercentage = typeof rawDucking === "number" ? rawDucking : Number(rawDucking) || 50;
  const startOffsetSeconds = typeof rawOffset === "number" ? rawOffset : Number(rawOffset) || 0;

  const trackLength = 180;
  const offsetVal = Math.max(0, startOffsetSeconds);
  const intervalVal = Math.max(5, intervalSeconds);
  const markers: number[] = [];
  if (intervalVal > 0 && offsetVal < trackLength) {
    for (let time = offsetVal; time < trackLength; time += intervalVal) {
      markers.push(time);
    }
  }

  const handleFileChange = useCallback(async (selected: File | null) => {
    setClientError(null);
    if (!selected) {
      setFile(null);
      setFileDuration(0);
      return;
    }
    const validationError = validateAudioFile(selected);
    if (validationError) {
      setClientError(validationError);
      setFile(null);
      setFileDuration(0);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setFile(selected);

    const duration = Math.round(await readAudioDuration(selected));
    setFileDuration(duration);
    if (duration <= 0) {
      setClientError(tErrors("invalidDuration"));
    } else {
      setClientError(null);
    }

    const currentTitle = getValues("title");
    if (!currentTitle || currentTitle.trim() === "") {
      const fileNameWithoutExt = selected.name.replace(/\.[^/.]+$/, "");
      setValue("title", fileNameWithoutExt, { shouldValidate: true });
    }
  }, [validateAudioFile, getValues, setValue, tErrors]);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    const droppedFile = e.dataTransfer.files?.[0];
    if (droppedFile) {
      handleFileChange(droppedFile);
    }
  };

  // A reload midway through discards the transfer with nothing to resume from, so make the browser ask.
  useEffect(() => {
    if (!isBusy) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isBusy]);

  // The poll loop outlives a render, so unmounting has to tell it to stop rather than leave it
  // updating state on a component that is gone. The flag is cleared on the way in as well: a
  // remount — StrictMode's double-invoke in dev, or the user coming back to the page — starts a
  // fresh form, and a stale `true` left by the previous cleanup would abandon its upload before it
  // began.
  useEffect(() => {
    abandonedRef.current = false;
    return () => {
      abandonedRef.current = true;
    };
  }, []);

  const cancelUpload = useCallback(() => {
    if (xhrRef.current) {
      xhrRef.current.abort();
      xhrRef.current = null;
    }
    setUploadStep("idle");
    setUploadProgress(0);
    toast.info(t("uploadCancelled"));
  }, [t]);

  const uploadToStorage = (presignedUrl: string, audioFile: File): Promise<void> => {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhrRef.current = xhr;

      xhr.upload.addEventListener("progress", (e) => {
        if (e.lengthComputable) {
          setUploadProgress(Math.round((e.loaded / e.total) * 100));
        }
      });

      xhr.addEventListener("load", () => {
        xhrRef.current = null;
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve();
        } else {
          reject(new Error(tCommon("error")));
        }
      });

      xhr.addEventListener("error", () => {
        xhrRef.current = null;
        reject(new Error(tCommon("error")));
      });

      xhr.addEventListener("abort", () => {
        xhrRef.current = null;
        reject(new DOMException("Upload aborted", "AbortError"));
      });

      xhr.open("PUT", presignedUrl);
      xhr.setRequestHeader("Content-Type", audioFile.type || "audio/mpeg");
      xhr.send(audioFile);
    });
  };

  /**
   * Polls until the merge settles, and answers with the status it settled on — or `PROCESSING` if the
   * wait ran out. A read that fails is not treated as the merge failing: the merge runs server-side
   * and a single dropped request says nothing about it, so the loop just tries again.
   */
  const waitForMerge = async (songId: string): Promise<SongStatus> => {
    setMergingSongId(songId);
    setUploadStep("merging");
    const deadline = Date.now() + MERGE_WAIT_LIMIT_MS;

    while (Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, MERGE_POLL_INTERVAL_MS));
      if (abandonedRef.current) return "PROCESSING";

      try {
        const res = await getSong({ songId });
        const status = res.data?.status;
        if (status && status !== "PROCESSING") {
          return status;
        }
      } catch {
        // Keep waiting; the next poll is the retry.
      }
    }
    return "PROCESSING";
  };

  const leaveMergeRunning = () => {
    if (!mergingSongId) return;
    abandonedRef.current = true;
    toast.info(t("uploadQueued"));
    router.push(`/dashboard/songs/${mergingSongId}`);
  };

  const submitSong = async (values: UploadSongFormValues) => {
    if (!file) {
      setClientError(tErrors("fileRequired"));
      return;
    }
    if (fileDuration <= 0) {
      setClientError(tErrors("invalidDuration"));
      return;
    }
    if (values.attachVoiceTag && !values.voiceTagId) {
      setClientError(t("selectVoiceTagPlaceholder"));
      return;
    }
    setClientError(null);

    try {
      setUploadStep("preparing");
      setUploadProgress(0);

      const ext = file.name.split(".").pop()?.toLowerCase() || "mp3";
      const presignedRes = await getPresignedUploadUrl({ format: ext });

      if (!presignedRes.success || !presignedRes.data) {
        throw new Error(presignedRes.message || tCommon("error"));
      }

      const { storageKey, url } = presignedRes.data;

      setUploadStep("uploading");
      await uploadToStorage(url, file);

      setUploadStep("creating");

      const uploadPayload: CreateSongRequest = {
        title: values.title,
        originalS3Key: storageKey,
        durationSeconds: fileDuration,
        format: ext,
      };

      if (values.attachVoiceTag && values.voiceTagId) {
        uploadPayload.voiceTagConfig = {
          voiceTagId: values.voiceTagId,
          intervalSeconds: Number(values.intervalSeconds) || 30,
          volumePercentage: Number(values.volumePercentage) || 80,
          duckingPercentage: Number(values.duckingPercentage) || 50,
          startOffsetSeconds: Number(values.startOffsetSeconds) || 0,
          enabled: true,
        };
      }

      const response = await createSong(uploadPayload);

      if (response.success && response.data) {
        // This flow posts directly rather than through useCreateSong, so nothing else would tell the
        // list its cache is out of date.
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });

        const songId = response.data.id;
        // A song with a voice tag is not the song the user asked for until the merge lands, so the
        // wait stays here rather than handing them a detail page for a half-finished song. Uploading
        // and merging are one errand; splitting them across two screens made the second half look
        // like a problem.
        const finalStatus =
          response.data.status === "PROCESSING" ? await waitForMerge(songId) : response.data.status;

        if (abandonedRef.current) return;

        if (finalStatus === "FAILED") {
          toast.error(t("processingFailed"));
        } else if (finalStatus === "PROCESSING") {
          // Still going after the cap; the song's own page takes over the watch.
          toast.info(t("uploadQueued"));
        } else {
          toast.success(t("uploadSuccess"));
        }

        onSuccess?.();
        // Straight into the song rather than back to the list: it is the thing the user just made.
        router.push(`/dashboard/songs/${songId}`);
      } else {
        toast.error(response.message || tCommon("error"));
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      const apiErr = asApiError((error) => {
        toast.error(resolveVoiceErrorMessage(error, tErrors, tCommon));
      });
      if (typeof err === "object" && err !== null && "status" in err) {
        apiErr(err as never);
      } else {
        toast.error((err as Error).message || tCommon("error"));
      }
    } finally {
      setUploadStep("idle");
      setUploadProgress(0);
      xhrRef.current = null;
    }
  };

  const fileSizeMB = file ? (file.size / 1024 / 1024).toFixed(2) : "0";

  return (
    <form
      // handleSubmit runs inside the event, not during render: the upload path reads xhrRef, and
      // building the handler while rendering made that look like a ref access mid-render.
      onSubmit={(event) => void handleSubmit(submitSong)(event)}
      className="flex flex-1 flex-col gap-5"
    >
      <input
        id="song-file"
        ref={fileInputRef}
        type="file"
        accept="audio/mpeg,audio/mp3,audio/wav,audio/wave,audio/flac,audio/x-flac,.mp3,.wav,.flac"
        onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
        className="sr-only"
        disabled={isBusy}
      />

      <div className="grid flex-1 gap-5 lg:grid-cols-2 lg:gap-6">
        <div className="flex flex-col gap-2">
          <Label htmlFor="song-file" className="text-sm font-medium">
            {t("fileLabel")} <span className="text-destructive">*</span>
          </Label>

          {!file ? (
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`group flex flex-1 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed p-6 text-center beat-16th transition-colors ease-hammer sm:p-8 ${
                isDragging
                  ? "border-foreground bg-muted"
                  : "border-border hover:border-foreground/40 hover:bg-muted/50"
              }`}
            >
              <span className="flex size-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                <UploadCloud className="size-5" aria-hidden="true" />
              </span>
              <p className="mt-3 text-sm font-medium text-foreground">{t("dropzoneTitle")}</p>
              <p className="mt-1 text-xs text-muted-foreground">{t("dropzoneHint")}</p>
            </div>
          ) : isBusy ? (
            <div className="flex flex-1 flex-col gap-4 rounded-2xl border border-border bg-muted/40 p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3">
                <SelectedFileSummary
                  fileName={file.name}
                  sizeLabel={`${fileSizeMB} MB`}
                  durationSeconds={fileDuration}
                />
                {uploadStep === "uploading" && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={cancelUpload}
                    className="shrink-0"
                    aria-label={t("cancelUpload")}
                  >
                    <X className="mr-1 size-3.5" aria-hidden="true" />
                    {t("cancelUpload")}
                  </Button>
                )}
                {uploadStep === "merging" && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={leaveMergeRunning}
                    className="shrink-0"
                  >
                    {t("mergeRunInBackground")}
                  </Button>
                )}
              </div>

              <UploadProgress
                phase={uploadStep === "creating" ? "finalizing" : uploadStep}
                percent={uploadProgress}
                loadedBytes={(file.size * uploadProgress) / 100}
                totalBytes={file.size}
              />

              {uploadStep === "merging" && (
                <p className="text-xs leading-relaxed text-muted-foreground">{t("mergeWaitHint")}</p>
              )}
            </div>
          ) : (
            <div className="group flex flex-1 flex-col justify-center gap-4 rounded-2xl border border-border bg-muted/40 p-4 beat-16th transition-colors ease-hammer hover:bg-muted/60 sm:p-5">
              <div className="flex items-start justify-between gap-3">
                <SelectedFileSummary
                  fileName={file.name}
                  sizeLabel={`${fileSizeMB} MB`}
                  durationSeconds={fileDuration}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="shrink-0"
                  onClick={() => {
                    setFile(null);
                    setFileDuration(0);
                    if (fileInputRef.current) fileInputRef.current.value = "";
                  }}
                >
                  <RefreshCcw className="mr-1 size-3.5" aria-hidden="true" />
                  {t("changeFile")}
                </Button>
              </div>
              <FileWaveform className="h-6 w-full opacity-60 beat-16th transition-opacity ease-hammer group-hover:opacity-100" />
            </div>
          )}
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="song-title" className="text-sm font-medium">
              {t("titleLabel")} <span className="text-destructive">*</span>
            </Label>
            <Input
              id="song-title"
              placeholder={t("titleLabel")}
              {...register("title")}
              maxLength={200}
              disabled={isBusy}
              aria-describedby={errors.title ? "song-title-error" : undefined}
              className="h-10"
            />
            {errors.title && (
              <p id="song-title-error" role="alert" className="text-xs text-destructive">
                {tValidation(errors.title.message as never)}
              </p>
            )}
          </div>

          <div className="flex flex-1 flex-col gap-4 rounded-xl border border-border bg-muted/40 p-4">
            <label className="flex cursor-pointer select-none items-center gap-3 text-sm font-medium text-foreground">
              <input
                type="checkbox"
                className="size-4 rounded border-border accent-primary"
                disabled={isBusy}
                {...register("attachVoiceTag")}
              />
              <span>{t("attachVoiceTag")}</span>
            </label>

            {!attachVoiceTag && (
              <div className="flex flex-1 flex-col justify-center gap-4 border-t border-dashed border-border/60 pt-4">
                <p className="text-xs leading-relaxed text-muted-foreground">
                  {t("attachVoiceTagDesc")}
                </p>
                <ol className="flex flex-col gap-2 text-xs leading-relaxed text-muted-foreground">
                  <li className="flex gap-2">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-foreground/10 text-[10px] font-bold text-foreground">1</span>
                    {t("attachVoiceTagStep1")}
                  </li>
                  <li className="flex gap-2">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-foreground/10 text-[10px] font-bold text-foreground">2</span>
                    {t("attachVoiceTagStep2")}
                  </li>
                  <li className="flex gap-2">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-foreground/10 text-[10px] font-bold text-foreground">3</span>
                    {t("attachVoiceTagStep3")}
                  </li>
                </ol>
              </div>
            )}

            {attachVoiceTag &&
              (voiceTags.length === 0 ? (
                <p className="rounded-lg border border-border bg-card p-3 text-xs text-muted-foreground">
                  {t("noVoiceTags")}
                </p>
              ) : (
                <div className="flex flex-col gap-3 border-t border-border pt-4">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="voice-tag-select" className="text-xs font-medium text-muted-foreground">
                      {t("selectVoiceTag")}
                    </Label>
                    <VoiceTagPicker
                      id="voice-tag-select"
                      disabled={isBusy}
                      onSelect={(voiceTagId) => setValue("voiceTagId", voiceTagId)}
                    />
                  </div>

                  <div className="flex items-start gap-2.5 rounded-lg border border-border bg-card p-3 text-xs text-muted-foreground">
                    <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                    <p className="leading-relaxed">{t("duckingTooltip")}</p>
                  </div>
                </div>
              ))}
          </div>
        </div>
      </div>

      {attachVoiceTag && voiceTags.length > 0 && (
        <>
          <div className="flex flex-col gap-3 rounded-xl border border-border p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-sm font-medium text-foreground">{t("timelineTitle")}</span>
              <span className="rounded-full border border-border px-2.5 py-0.5 text-xs text-muted-foreground">
                {t("timelineInsertions", { count: markers.length })}
              </span>
            </div>

            <div className="relative flex h-12 w-full items-center overflow-hidden rounded-lg border border-border bg-muted/50 p-2">
              <div className="absolute inset-x-2 top-2 bottom-2 flex items-center justify-between gap-0.5 opacity-25">
                {Array.from({ length: TIMELINE_TICKS }).map((_, i) => (
                  <span
                    key={i}
                    className="w-1 rounded-full bg-foreground"
                    style={{ height: `${i % 5 === 0 ? 80 : i % 2 === 0 ? 50 : 30}%` }}
                  />
                ))}
              </div>

              <div className="relative h-full w-full">
                {markers.map((timeSec) => (
                  <div
                    key={timeSec}
                    className="absolute top-0 bottom-0 flex -translate-x-1/2 flex-col items-center"
                    style={{ left: `${(timeSec / trackLength) * 100}%` }}
                  >
                    <span className="h-full w-0.5 bg-primary" />
                    <span className="absolute -top-1 rounded bg-primary px-1 text-xs text-primary-foreground">
                      {timeSec}s
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
              <p>
                {t("timelinePositions")}{" "}
                <span className="text-foreground">
                  {markers.length > 0
                    ? markers.slice(0, 8).map((m) => `${m}s`).join(", ") +
                      (markers.length > 8 ? "..." : "")
                    : "0s"}
                </span>
              </p>
              <p>{t("duckingStatus", { percent: String(duckingPercentage) })}</p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <SliderField
              id="interval-seconds"
              label={t("intervalSeconds")}
              hint={t("intervalHint")}
              unit="s"
              min={5}
              max={600}
              value={intervalSeconds}
              disabled={isBusy}
              registration={register("intervalSeconds")}
              onSlide={(next) => setValue("intervalSeconds", next)}
            />
            <SliderField
              id="volume-percentage"
              label={t("volumePercentage")}
              hint={t("volumeHint")}
              unit="%"
              min={0}
              max={100}
              value={volumePercentage}
              disabled={isBusy}
              registration={register("volumePercentage")}
              onSlide={(next) => setValue("volumePercentage", next)}
            />
            <SliderField
              id="ducking-percentage"
              label={t("duckingPercentage")}
              hint={t("duckingHint")}
              unit="%"
              min={0}
              max={100}
              value={duckingPercentage}
              disabled={isBusy}
              registration={register("duckingPercentage")}
              onSlide={(next) => setValue("duckingPercentage", next)}
            />
            <SliderField
              id="start-offset"
              label={t("startOffsetSeconds")}
              hint={t("startOffsetHint")}
              unit="s"
              min={0}
              max={120}
              value={startOffsetSeconds}
              disabled={isBusy}
              registration={register("startOffsetSeconds")}
              onSlide={(next) => setValue("startOffsetSeconds", next)}
            />
          </div>
        </>
      )}

      {clientError && (
        <p
          role="alert"
          className="rounded-xl border border-destructive/30 bg-destructive/10 p-3.5 text-sm text-destructive"
        >
          {clientError}
        </p>
      )}

      <div className="flex items-center justify-end gap-3">
        {onCancel && (
          <Button type="button" variant="ghost" size="lg" onClick={onCancel} disabled={isBusy}>
            {tActions("cancel")}
          </Button>
        )}
        <Button
          type="submit"
          size="lg"
          disabled={isBusy || !file}
          className="h-11 min-w-36 font-semibold sm:h-9"
        >
          {isBusy ? (
            <>
              <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />
              <span>
                {uploadStep === "preparing" && tUpload("preparing")}
                {uploadStep === "uploading" && tUpload("sending", { percent: uploadProgress })}
                {uploadStep === "creating" && tUpload("finalizing")}
                {uploadStep === "merging" && tUpload("merging")}
              </span>
            </>
          ) : (
            t("submitButton")
          )}
        </Button>
      </div>
    </form>
  );
}
