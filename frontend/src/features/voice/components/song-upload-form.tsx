"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { getPresignedUploadUrl, uploadSong } from "../api/songs";
import { useListVoiceTags } from "../api/voice-tags";
import { useFileValidation } from "../hooks/use-file-validation";
import { uploadSongFormSchema, type UploadSongFormValues, type UploadSongFormInput } from "../schemas/song-schema";

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
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const router = useRouter();

  const { validateAudioFile } = useFileValidation();
  const { data: voiceTagsRes } = useListVoiceTags({ page: 0, size: 50 });
  const voiceTags = voiceTagsRes?.data?.content ?? [];

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<UploadSongFormInput, unknown, UploadSongFormValues>({
    resolver: zodResolver(uploadSongFormSchema),
    defaultValues: {
      title: "",
      attachVoiceTag: false,
      voiceTagId: "",
      intervalSeconds: 10,
      volumePercentage: 80,
      fadeInDurationMs: 0,
      fadeOutDurationMs: 0,
      startOffsetSeconds: 0,
    },
  });

  const attachVoiceTag = useWatch({ control, name: "attachVoiceTag" });

  const handleFileChange = (selected: File | null) => {
    setClientError(null);
    if (!selected) {
      setFile(null);
      return;
    }
    const validationError = validateAudioFile(selected);
    if (validationError) {
      setClientError(validationError);
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setFile(selected);
  };

  const onSubmit = handleSubmit(async (values) => {
    if (!file) {
      setClientError(tErrors("fileRequired"));
      return;
    }
    if (values.attachVoiceTag && !values.voiceTagId) {
      setClientError(t("selectVoiceTagPlaceholder"));
      return;
    }
    setClientError(null);
    setIsUploading(true);

    try {
      const ext = file.name.split(".").pop()?.toLowerCase() || "mp3";
      const presignedRes = await getPresignedUploadUrl({ format: ext });

      if (!presignedRes.success || !presignedRes.data) {
        throw new Error(presignedRes.message || tCommon("error"));
      }

      const { originalS3Key, uploadUrl } = presignedRes.data;

      const putRes = await fetch(uploadUrl, {
        method: "PUT",
        body: file,
        headers: {
          "Content-Type": file.type || "audio/mpeg",
        },
      });

      if (!putRes.ok) {
        throw new Error(tCommon("error"));
      }

      const uploadPayload: Parameters<typeof uploadSong>[0] = {
        title: values.title,
        originalS3Key,
        fileSizeBytes: file.size,
        durationSeconds: 180,
        format: ext,
      };

      if (values.attachVoiceTag && values.voiceTagId) {
        uploadPayload.voiceTagConfig = {
          voiceTagId: values.voiceTagId,
          intervalSeconds: Number(values.intervalSeconds) || 10,
          volumePercentage: Number(values.volumePercentage) || 80,
          fadeInDurationMs: Number(values.fadeInDurationMs) || 0,
          fadeOutDurationMs: Number(values.fadeOutDurationMs) || 0,
          startOffsetSeconds: Number(values.startOffsetSeconds) || 0,
          enabled: true,
        };
      }

      const response = await uploadSong(uploadPayload);

      if (response.success) {
        toast.success(t("uploadSuccess"));
        onSuccess?.();
        router.push("/dashboard/songs");
      } else {
        toast.error(response.message || tCommon("error"));
      }
    } catch (err) {
      const apiErr = asApiError((error) => {
        toast.error(resolveVoiceErrorMessage(error, tErrors, tCommon));
      });
      if (typeof err === "object" && err !== null && "status" in err) {
        apiErr(err as never);
      } else {
        toast.error((err as Error).message || tCommon("error"));
      }
    } finally {
      setIsUploading(false);
    }
  });

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="song-file">{t("fileLabel")}</Label>
        <input
          id="song-file"
          ref={fileInputRef}
          type="file"
          accept="audio/mpeg,audio/mp3,audio/wav,audio/wave,audio/flac,audio/x-flac,.mp3,.wav,.flac"
          onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
          className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm file:mr-2 file:rounded file:border-0 file:bg-neutral-100 file:px-2 file:py-1 file:text-xs file:text-neutral-700 dark:border-input dark:bg-input/30 dark:file:bg-neutral-800 dark:file:text-neutral-200"
        />
        {file && (
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {file.name} - {(file.size / 1024 / 1024).toFixed(2)} MB
          </span>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="song-title">{t("titleLabel")}</Label>
        <Input id="song-title" {...register("title")} maxLength={256} />
        {errors.title && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.title.message as never)}
          </p>
        )}
      </div>

      <div className="rounded-xl border border-neutral-200 p-4 dark:border-neutral-800 flex flex-col gap-3">
        <label className="flex items-center gap-3 cursor-pointer select-none text-sm font-medium text-black dark:text-white">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-neutral-300 text-black focus:ring-black dark:border-neutral-700"
            {...register("attachVoiceTag")}
          />
          {t("attachVoiceTag")}
        </label>

        {attachVoiceTag && (
          <div className="flex flex-col gap-3 pt-2">
            {voiceTags.length === 0 ? (
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                {t("noVoiceTags")}
              </p>
            ) : (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="voice-tag-select">{t("selectVoiceTag")}</Label>
                  <select
                    id="voice-tag-select"
                    className="h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm dark:border-neutral-800 dark:bg-black dark:text-white"
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

                <div className="grid grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="interval-seconds">{t("intervalSeconds")}</Label>
                    <Input
                      id="interval-seconds"
                      type="number"
                      min={1}
                      {...register("intervalSeconds")}
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="volume-percentage">{t("volumePercentage")}</Label>
                    <Input
                      id="volume-percentage"
                      type="number"
                      min={0}
                      max={100}
                      {...register("volumePercentage")}
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="fade-in-ms">{t("fadeInDurationMs")}</Label>
                    <Input
                      id="fade-in-ms"
                      type="number"
                      min={0}
                      placeholder="0"
                      {...register("fadeInDurationMs")}
                    />
                    <p className="text-xs text-neutral-400 dark:text-neutral-500">{t("fadeInHint")}</p>
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="fade-out-ms">{t("fadeOutDurationMs")}</Label>
                    <Input
                      id="fade-out-ms"
                      type="number"
                      min={0}
                      placeholder="0"
                      {...register("fadeOutDurationMs")}
                    />
                    <p className="text-xs text-neutral-400 dark:text-neutral-500">{t("fadeOutHint")}</p>
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="start-offset">{t("startOffsetSeconds")}</Label>
                    <Input
                      id="start-offset"
                      type="number"
                      min={0}
                      placeholder="0"
                      {...register("startOffsetSeconds")}
                    />
                    <p className="text-xs text-neutral-400 dark:text-neutral-500">{t("startOffsetHint")}</p>
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </div>

      {isUploading && (
        <div className="flex items-center gap-2 text-sm text-neutral-500">
          <Spinner size="sm" />
          <span>{t("uploadingProgress")}</span>
        </div>
      )}

      {clientError && (
        <div
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/30 dark:text-red-400"
        >
          {clientError}
        </div>
      )}

      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isUploading}>
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" disabled={isUploading || !file}>
          {t("submitButton")}
        </Button>
      </div>
    </form>
  );
}
