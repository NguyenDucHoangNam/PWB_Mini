"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import {
  requestUploadUrl,
  createSong,
} from "@/features/audio";
import type { UploadUrlResponse } from "@/features/audio/types";

const ALLOWED_CONTENT_TYPES = [
  "audio/wav",
  "audio/wave",
  "audio/flac",
  "audio/x-flac",
  "audio/mp3",
  "audio/mpeg",
] as const;

const AUDIO_FORMAT_MAP: Record<string, string> = {
  "audio/wav": "wav",
  "audio/wave": "wav",
  "audio/flac": "flac",
  "audio/x-flac": "flac",
  "audio/mp3": "mp3",
  "audio/mpeg": "mp3",
};

type UploadStep = "form" | "uploading" | "confirming" | "done";

interface UploadDemoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function UploadDemoModal({ open, onOpenChange, onSuccess }: UploadDemoModalProps) {
  const t = useTranslations("dashboard.modals");
  const tCommon = useTranslations("dashboard.common");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const xhrRef = useRef<XMLHttpRequest | null>(null);

  const [step, setStep] = useState<UploadStep>("form");
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [presigned, setPresigned] = useState<UploadUrlResponse | null>(null);

  useEffect(() => {
    if (!open) {
      setStep("form");
      setFile(null);
      setTitle("");
      setProgress(0);
      setError(null);
      setPresigned(null);
      if (xhrRef.current) {
        xhrRef.current.abort();
        xhrRef.current = null;
      }
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }, [open]);

  const handleFileChange = (selected: File | null) => {
    if (!selected) {
      setFile(null);
      return;
    }
    if (!ALLOWED_CONTENT_TYPES.includes(selected.type as (typeof ALLOWED_CONTENT_TYPES)[number])) {
      setError(t("uploadErrorFile"));
      setFile(null);
      return;
    }
    setError(null);
    setFile(selected);
  };

  const requestUrl = async () => {
    if (!file) {
      setError(t("uploadErrorFile"));
      return false;
    }
    const trimmedTitle = title.trim();
    if (trimmedTitle.length < 2 || trimmedTitle.length > 200) {
      setError(t("uploadErrorTitle"));
      return false;
    }
    try {
      const format = AUDIO_FORMAT_MAP[file.type] || "mp3";
      const res = await requestUploadUrl({
        data: { format },
      });
      if (!res.success || !res.data) {
        setError(res.message || tCommon("error"));
        return false;
      }
      setPresigned(res.data);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : tCommon("error"));
      return false;
    }
  };

  const uploadToStorage = () => {
    if (!presigned || !file) return;
    setStep("uploading");
    setProgress(0);
    const xhr = new XMLHttpRequest();
    xhrRef.current = xhr;
    xhr.upload.addEventListener("progress", (e) => {
      if (e.lengthComputable) {
        setProgress(Math.round((e.loaded / e.total) * 100));
      }
    });
    xhr.addEventListener("load", () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        createSongNow();
      } else {
        setStep("form");
        setError(tCommon("error"));
      }
    });
    xhr.addEventListener("error", () => {
      setStep("form");
      setError(tCommon("error"));
    });
    xhr.addEventListener("abort", () => {
      setStep("form");
    });
    xhr.open("PUT", presigned.url);
    xhr.setRequestHeader("Content-Type", file.type);
    xhr.send(file);
  };

  const createSongNow = async () => {
    if (!presigned) return;
    setStep("confirming");
    try {
      const format = AUDIO_FORMAT_MAP[file?.type || ""] || "mp3";
      const res = await createSong({
        data: {
          title: title.trim(),
          originalS3Key: presigned.storageKey,
          durationSeconds: 0,
          format,
        },
      });
      if (!res.success) {
        setError(res.message || tCommon("error"));
        setStep("form");
        return;
      }
      setStep("done");
      toast.success(t("uploadSuccess"));
      onSuccess?.();
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : tCommon("error"));
      setStep("form");
    }
  };

  const handleContinue = async () => {
    setError(null);
    const ok = await requestUrl();
    if (ok) uploadToStorage();
  };

  const handleRetry = async () => {
    setError(null);
    uploadToStorage();
  };

  const isBusy = step !== "form" && step !== "done";

  return (
    <Dialog open={open} onOpenChange={(o) => !isBusy && onOpenChange(o)}>
      {open ? (
        <DialogContent className="sm:max-w-lg" showCloseButton={!isBusy}>
          <DialogHeader>
            <DialogTitle>{t("uploadTitle")}</DialogTitle>
            <DialogDescription>{t("uploadDesc")}</DialogDescription>
          </DialogHeader>

          {step === "form" && (
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="upload-file">{t("uploadFileLabel")}</Label>
                <input
                  id="upload-file"
                  ref={fileInputRef}
                  type="file"
                  accept={ALLOWED_CONTENT_TYPES.join(",")}
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
                <Label htmlFor="upload-title">{t("uploadTitleLabel")}</Label>
                <Input
                  id="upload-title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder={t("uploadTitleLabel")}
                  maxLength={200}
                />
              </div>

              {error && (
                <div
                  role="alert"
                  className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/30 dark:text-red-400"
                >
                  {error}
                </div>
              )}

              <DialogFooter className="-mx-4 -mb-4">
                <Button variant="ghost" onClick={() => onOpenChange(false)}>
                  {t("uploadCancel")}
                </Button>
                <Button
                  onClick={handleContinue}
                  disabled={!file || title.trim().length < 2}
                >
                  {t("uploadNext")}
                </Button>
              </DialogFooter>
            </div>
          )}

          {(step === "uploading" || step === "confirming") && (
            <div className="flex flex-col items-center gap-4 py-6">
              <Spinner size="lg" />
              {step === "uploading" ? (
                <p className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  {t("uploadProgress", { percent: progress })}
                </p>
              ) : (
                <p className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  {tCommon("loading")}
                </p>
              )}
              {step === "uploading" && progress === 0 && (
                <p className="text-xs text-neutral-500">Preparing...</p>
              )}
              {error && (
                <div
                  role="alert"
                  className="rounded-lg bg-red-50 p-3 text-xs font-semibold text-red-600 dark:bg-red-950/30 dark:text-red-400"
                >
                  {error}
                </div>
              )}
              <Button variant="outline" onClick={handleRetry}>
                {t("uploadRetry")}
              </Button>
            </div>
          )}
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
