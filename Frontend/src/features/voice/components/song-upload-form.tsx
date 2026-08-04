"use client";

import { useRef, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useForm, useWatch } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Info, UploadCloud, FileAudio, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { getPresignedUploadUrl, createSong, SONGS_KEY } from "../api/songs";
import { useListVoiceTags } from "../api/voice-tags";
import { useFileValidation } from "../hooks/use-file-validation";
import { uploadSongFormSchema, type UploadSongFormValues, type UploadSongFormInput } from "../schemas/song-schema";
import type { CreateSongRequest } from "../types";

type UploadStep = "idle" | "preparing" | "uploading" | "creating";

interface SongUploadFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

/**
 * Exact, but decodes the entire file into memory as PCM — a 100 MB MP3 can balloon past a gigabyte.
 * Only worth paying when the cheap path below could not read the duration at all.
 */
async function decodeAudioDuration(file: File): Promise<number> {
  try {
    const arrayBuffer = await file.arrayBuffer();
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioCtx) {
      const audioCtx = new AudioCtx();
      const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);
      const duration = audioBuffer.duration;
      await audioCtx.close();
      if (Number.isFinite(duration) && duration > 0) {
        return Math.round(duration);
      }
    }
  } catch {
  }
  return 0;
}

/** Reads the container header only: no full decode, so memory stays flat regardless of file size. */
function readDurationFromMetadata(file: File): Promise<number> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = "metadata";

    const timeoutId = setTimeout(() => {
      URL.revokeObjectURL(url);
      resolve(0);
    }, 5000);

    audio.addEventListener("loadedmetadata", () => {
      clearTimeout(timeoutId);
      let dur = audio.duration;
      if (dur === Infinity || Number.isNaN(dur)) {
        audio.currentTime = 1e101;
        audio.ontimeupdate = () => {
          audio.ontimeupdate = null;
          dur = audio.duration;
          URL.revokeObjectURL(url);
          resolve(Number.isFinite(dur) && dur > 0 ? Math.round(dur) : 0);
        };
      } else {
        URL.revokeObjectURL(url);
        resolve(Number.isFinite(dur) && dur > 0 ? Math.round(dur) : 0);
      }
    });

    audio.addEventListener("error", () => {
      clearTimeout(timeoutId);
      URL.revokeObjectURL(url);
      resolve(0);
    });

    audio.src = url;
  });
}

async function readAudioDuration(file: File): Promise<number> {
  const fromMetadata = await readDurationFromMetadata(file);
  if (fromMetadata > 0) {
    return fromMetadata;
  }
  // Some VBR MP3s and exotic containers report no usable duration in the header; decoding is the only
  // way left to find out.
  return decodeAudioDuration(file);
}

export function SongUploadForm({ onCancel, onSuccess }: SongUploadFormProps) {
  const t = useTranslations("voice.songs.form");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const xhrRef = useRef<XMLHttpRequest | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileDuration, setFileDuration] = useState<number>(0);
  const [isDragging, setIsDragging] = useState(false);
  const [clientError, setClientError] = useState<string | null>(null);
  const [uploadStep, setUploadStep] = useState<UploadStep>("idle");
  const [uploadProgress, setUploadProgress] = useState(0);
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

    const duration = await readAudioDuration(selected);
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

      if (response.success) {
        // This flow posts directly rather than through useCreateSong, so nothing else would tell the
        // list its cache is out of date.
        queryClient.invalidateQueries({ queryKey: [SONGS_KEY] });
        toast.success(t("uploadSuccess"));
        onSuccess?.();
        router.push("/dashboard/songs");
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
  const loadedMB = file ? ((uploadProgress / 100) * file.size / 1024 / 1024).toFixed(2) : "0";

  return (
    <form
      // handleSubmit runs inside the event, not during render: the upload path reads xhrRef, and
      // building the handler while rendering made that look like a ref access mid-render.
      onSubmit={(event) => void handleSubmit(submitSong)(event)}
      className="flex flex-col gap-5"
    >
      <div className="flex flex-col gap-2">
        <Label htmlFor="song-file" className="font-semibold text-sm">
          {t("fileLabel")} <span className="text-destructive">*</span>
        </Label>

        <input
          id="song-file"
          ref={fileInputRef}
          type="file"
          accept="audio/mpeg,audio/mp3,audio/wav,audio/wave,audio/flac,audio/x-flac,.mp3,.wav,.flac"
          onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
          className="sr-only"
          disabled={isBusy}
        />

        {!file ? (
          <div
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`group relative flex flex-col items-center justify-center cursor-pointer rounded-xl border-2 border-dashed p-8 text-center transition-colors ${
              isDragging
                ? "border-primary bg-accent/50 scale-[0.99]"
                : "border-border bg-muted/30 hover:border-neutral-400 dark:hover:border-neutral-600"
            }`}
          >
            <div className="flex size-12 items-center justify-center rounded-xl bg-background border border-border shadow-xs">
              <UploadCloud className="size-6 text-foreground" />
            </div>
            <p className="mt-3 text-sm font-semibold text-foreground">
              {t("dropzoneTitle")}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {t("dropzoneHint")}
            </p>
          </div>
        ) : isBusy ? (
          <div className="rounded-xl border border-border bg-card p-4 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3.5 min-w-0">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                  <FileAudio className="size-5" />
                </div>
                <div className="min-w-0 space-y-0.5">
                  <p className="text-sm font-semibold text-foreground truncate">
                    {file.name}
                  </p>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <span>{fileSizeMB} MB</span>
                    <span>•</span>
                    <span className="uppercase font-medium text-foreground bg-muted px-1.5 py-0.5 rounded text-[10px]">
                      {file.name.split(".").pop()}
                    </span>
                    {fileDuration > 0 && (
                      <>
                        <span>•</span>
                        <span>{Math.floor(fileDuration / 60)}:{String(fileDuration % 60).padStart(2, "0")}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>
              {uploadStep === "uploading" && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={cancelUpload}
                  className="text-xs shrink-0"
                  aria-label={t("cancelUpload")}
                >
                  <X className="size-3.5 mr-1" aria-hidden="true" />
                  {t("cancelUpload")}
                </Button>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all duration-300 ease-out"
                  style={{ width: `${uploadStep === "uploading" ? uploadProgress : uploadStep === "creating" ? 100 : 5}%` }}
                />
                {uploadStep === "uploading" && (
                  <div className="absolute inset-0 overflow-hidden rounded-full">
                    <div className="h-full w-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/20 to-transparent" />
                  </div>
                )}
              </div>

              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  {/* This block only renders while busy, so the spinner always belongs here. */}
                  <Loader2 className="size-3 animate-spin" aria-hidden="true" />
                  {uploadStep === "preparing" && t("uploadPreparing")}
                  {uploadStep === "uploading" && t("uploadProgress", { percent: uploadProgress })}
                  {uploadStep === "creating" && t("uploadCreating")}
                </span>
                {uploadStep === "uploading" && (
                  <span className="font-medium tabular-nums">
                    {t("uploadProgressDetail", { loaded: loadedMB, total: fileSizeMB })}
                  </span>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4">
            <div className="flex items-center gap-3.5 min-w-0">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <FileAudio className="size-5" />
              </div>
              <div className="min-w-0 space-y-0.5">
                <p className="text-sm font-semibold text-foreground truncate">
                  {file.name}
                </p>
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span>{fileSizeMB} MB</span>
                  <span>•</span>
                  <span className="uppercase font-medium text-foreground bg-muted px-1.5 py-0.5 rounded text-[10px]">
                    {file.name.split(".").pop()}
                  </span>
                  {fileDuration > 0 && (
                    <>
                      <span>•</span>
                      <span>{Math.floor(fileDuration / 60)}:{String(fileDuration % 60).padStart(2, "0")}</span>
                    </>
                  )}
                </div>
              </div>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setFile(null);
                setFileDuration(0);
                if (fileInputRef.current) fileInputRef.current.value = "";
              }}
              className="text-xs"
            >
              {t("changeFile")}
            </Button>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="song-title" className="font-medium text-sm">
          {t("titleLabel")} <span className="text-destructive">*</span>
        </Label>
        <Input
          id="song-title"
          placeholder={t("titleLabel")}
          {...register("title")}
          maxLength={200}
          disabled={isBusy}
          aria-describedby={errors.title ? "song-title-error" : undefined}
          className="h-10 text-sm"
        />
        {errors.title && (
          <p id="song-title-error" role="alert" className="text-xs text-destructive">
            {tValidation(errors.title.message as never)}
          </p>
        )}
      </div>

      <div className="rounded-xl border border-border bg-card p-5 flex flex-col gap-5">
        <label className="flex items-center gap-3 cursor-pointer select-none text-sm font-medium text-foreground">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-input text-primary focus:ring-ring"
            disabled={isBusy}
            {...register("attachVoiceTag")}
          />
          <span>{t("attachVoiceTag")}</span>
        </label>

        {attachVoiceTag && (
          <div className="flex flex-col gap-5 pt-3 border-t border-border">
            {voiceTags.length === 0 ? (
              <p className="text-xs text-muted-foreground bg-muted p-3 rounded-lg border border-border">
                {t("noVoiceTags")}
              </p>
            ) : (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="voice-tag-select" className="font-medium text-xs text-muted-foreground">
                    {t("selectVoiceTag")}
                  </Label>
                  <select
                    id="voice-tag-select"
                    className="h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    disabled={isBusy}
                    {...register("voiceTagId")}
                  >
                    <option value="">-- {t("selectVoiceTagPlaceholder")} --</option>
                    {voiceTags.map((vt) => (
                      <option key={vt.id} value={vt.id}>
                        {vt.name} ({vt.tagType})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/40 p-3 text-xs text-foreground">
                  <Info className="size-4 text-muted-foreground shrink-0 mt-0.5" />
                  <div className="space-y-0.5">
                    <p className="font-semibold">{t("duckingPercentage")}</p>
                    <p className="text-muted-foreground leading-relaxed">
                      {t("duckingTooltip")}
                    </p>
                  </div>
                </div>

                <div className="flex flex-col gap-3 rounded-xl border border-border bg-muted/20 p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-foreground">{t("timelineTitle")}</span>
                    <span className="text-[11px] font-medium text-muted-foreground bg-muted px-2.5 py-0.5 rounded-full border border-border">
                      {t("timelineInsertions", { count: markers.length })}
                    </span>
                  </div>

                  <div className="relative h-12 w-full rounded-lg border border-border bg-background p-2 flex items-center overflow-hidden">
                    <div className="absolute inset-x-2 top-2 bottom-2 flex items-center justify-between gap-0.5 opacity-20">
                      {Array.from({ length: 50 }).map((_, i) => (
                        <div
                          key={i}
                          className="w-1 bg-foreground rounded-full"
                          style={{
                            height: `${(i % 5 === 0 ? 80 : (i % 2 === 0 ? 50 : 30))}%`,
                          }}
                        />
                      ))}
                    </div>

                    <div className="relative h-full w-full">
                      {markers.map((timeSec, idx) => {
                        const leftPct = (timeSec / trackLength) * 100;
                        return (
                          <div
                            key={idx}
                            className="absolute top-0 bottom-0 flex flex-col items-center -translate-x-1/2"
                            style={{ left: `${leftPct}%` }}
                          >
                            <div className="h-full w-0.5 bg-primary" />
                            <span className="absolute -top-1 rounded bg-primary px-1 text-[9px] font-bold text-primary-foreground shadow-xs">
                              {timeSec}s
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted-foreground">
                    <div>
                      <span>{t("timelinePositions")} </span>
                      <span className="font-semibold text-foreground">
                        {markers.length > 0 ? markers.slice(0, 8).map((m) => `${m}s`).join(", ") + (markers.length > 8 ? "..." : "") : "0s"}
                      </span>
                    </div>
                    <div>
                      {t("duckingStatus", { percent: String(duckingPercentage) })}
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="flex flex-col gap-2 rounded-xl border border-border bg-background p-4">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="interval-seconds" className="text-xs font-medium">
                        {t("intervalSeconds")}
                      </Label>
                      <div className="flex items-center gap-1">
                        <Input
                          id="interval-seconds"
                          type="number"
                          min={5}
                          max={600}
                          disabled={isBusy}
                          className="h-7 w-16 text-right text-xs font-semibold border-border px-1.5"
                          {...register("intervalSeconds")}
                        />
                        <span className="text-xs font-medium text-muted-foreground">s</span>
                      </div>
                    </div>
                    <input
                      type="range"
                      min={5}
                      max={600}
                      value={intervalSeconds}
                      disabled={isBusy}
                      onChange={(e) => setValue("intervalSeconds", Number(e.target.value))}
                      className="w-full accent-primary h-1.5 rounded-lg cursor-pointer bg-muted"
                    />
                    <p className="text-[11px] text-muted-foreground leading-tight">
                      {t("intervalHint")}
                    </p>
                  </div>

                  <div className="flex flex-col gap-2 rounded-xl border border-border bg-background p-4">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="volume-percentage" className="text-xs font-medium">
                        {t("volumePercentage")}
                      </Label>
                      <div className="flex items-center gap-1">
                        <Input
                          id="volume-percentage"
                          type="number"
                          min={0}
                          max={100}
                          disabled={isBusy}
                          className="h-7 w-16 text-right text-xs font-semibold border-border px-1.5"
                          {...register("volumePercentage")}
                        />
                        <span className="text-xs font-medium text-muted-foreground">%</span>
                      </div>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={volumePercentage}
                      disabled={isBusy}
                      onChange={(e) => setValue("volumePercentage", Number(e.target.value))}
                      className="w-full accent-primary h-1.5 rounded-lg cursor-pointer bg-muted"
                    />
                    <p className="text-[11px] text-muted-foreground leading-tight">
                      {t("volumeHint")}
                    </p>
                  </div>

                  <div className="flex flex-col gap-2 rounded-xl border border-border bg-background p-4">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="ducking-percentage" className="text-xs font-medium">
                        {t("duckingPercentage")}
                      </Label>
                      <div className="flex items-center gap-1">
                        <Input
                          id="ducking-percentage"
                          type="number"
                          min={0}
                          max={100}
                          disabled={isBusy}
                          className="h-7 w-16 text-right text-xs font-semibold border-border px-1.5"
                          {...register("duckingPercentage")}
                        />
                        <span className="text-xs font-medium text-muted-foreground">%</span>
                      </div>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={duckingPercentage}
                      disabled={isBusy}
                      onChange={(e) => setValue("duckingPercentage", Number(e.target.value))}
                      className="w-full accent-primary h-1.5 rounded-lg cursor-pointer bg-muted"
                    />
                    <p className="text-[11px] text-muted-foreground leading-tight">
                      {t("duckingHint")}
                    </p>
                  </div>

                  <div className="flex flex-col gap-2 rounded-xl border border-border bg-background p-4">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="start-offset" className="text-xs font-medium">
                        {t("startOffsetSeconds")}
                      </Label>
                      <div className="flex items-center gap-1">
                        <Input
                          id="start-offset"
                          type="number"
                          min={0}
                          max={120}
                          disabled={isBusy}
                          className="h-7 w-16 text-right text-xs font-semibold border-border px-1.5"
                          {...register("startOffsetSeconds")}
                        />
                        <span className="text-xs font-medium text-muted-foreground">s</span>
                      </div>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={120}
                      value={startOffsetSeconds}
                      disabled={isBusy}
                      onChange={(e) => setValue("startOffsetSeconds", Number(e.target.value))}
                      className="w-full accent-primary h-1.5 rounded-lg cursor-pointer bg-muted"
                    />
                    <p className="text-[11px] text-muted-foreground leading-tight">
                      {t("startOffsetHint")}
                    </p>
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </div>

      {clientError && (
        <div
          role="alert"
          className="rounded-xl border border-red-200 bg-red-50/80 p-3.5 text-xs font-semibold text-red-600 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-400"
        >
          {clientError}
        </div>
      )}

      <div className="flex items-center justify-end gap-3 pt-2">
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isBusy}>
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" disabled={isBusy || !file} className="min-w-32 font-semibold">
          {isBusy ? (
            <>
              <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />
              <span>
                {uploadStep === "preparing" && t("uploadPreparing")}
                {uploadStep === "uploading" && t("uploadProgress", { percent: uploadProgress })}
                {uploadStep === "creating" && t("uploadCreating")}
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
