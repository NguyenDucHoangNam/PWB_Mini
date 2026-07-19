"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import { useUploadSong } from "../api/songs";
import { useFileValidation } from "../hooks/use-file-validation";
import { uploadSongFormSchema, type UploadSongFormValues } from "../schemas/song-schema";

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
  const router = useRouter();

  const { validateAudioFile } = useFileValidation();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UploadSongFormValues>({
    resolver: zodResolver(uploadSongFormSchema),
    defaultValues: { title: "", artist: "", album: "" },
  });

  const { mutate: upload, isPending } = useUploadSong({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("uploadSuccess"));
          onSuccess?.();
          router.push("/dashboard");
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

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

  const onSubmit = handleSubmit((values) => {
    if (!file) {
      setClientError(tErrors("fileRequired"));
      return;
    }
    setClientError(null);

    const formData = new FormData();
    formData.append("file", file);
    formData.append(
      "metadata",
      new Blob(
        [
          JSON.stringify({
            title: values.title,
            artist: values.artist || undefined,
            album: values.album || undefined,
          }),
        ],
        { type: "application/json" }
      )
    );

    upload({ formData });
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

      <div className="flex flex-col gap-2">
        <Label htmlFor="song-artist">{t("artistLabel")}</Label>
        <Input id="song-artist" {...register("artist")} maxLength={256} />
        {errors.artist && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.artist.message as never)}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="song-album">{t("albumLabel")}</Label>
        <Input id="song-album" {...register("album")} maxLength={256} />
        {errors.album && (
          <p className="text-xs text-red-600 dark:text-red-400">
            {tValidation(errors.album.message as never)}
          </p>
        )}
      </div>

      {isPending && (
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
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isPending}>
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" disabled={isPending || !file}>
          {t("submitButton")}
        </Button>
      </div>
    </form>
  );
}