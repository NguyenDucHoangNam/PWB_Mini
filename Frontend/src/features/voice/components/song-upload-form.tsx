"use client";

import { useEffect, useRef, useState, useCallback } from "react";
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
import { UploadProgress } from "@/components/ui/upload-progress";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { readAudioDuration } from "../lib/read-audio-duration";
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

interface SongUploadFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
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
  // updating state on a component that is gone.
  useEffect(() => {
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
              {/* The file is already safe on the server by now, so leaving is only giving up the wait —
                  never the upload. Without this a slow queue would hold the user on this screen. */}
              {uploadStep === "merging" && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={leaveMergeRunning}
                  className="text-xs shrink-0"
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
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                {t("mergeWaitHint")}
              </p>
            )}
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
                  {/*
                    A dropdown of every tag stops being usable somewhere past a couple of dozen; this
                    searches instead, and each row shows the voice and language so two tags with similar
                    names stay distinguishable. The id itself stays in the form via setValue.
                  */}
                  <VoiceTagPicker
                    id="voice-tag-select"
                    disabled={isBusy}
                    onSelect={(voiceTagId) => setValue("voiceTagId", voiceTagId)}
                  />
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
