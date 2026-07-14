"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import {
  previewVoiceTag,
  useDeleteVoiceTag,
  useSetDefaultVoiceTag,
  useVoiceTags,
} from "@/features/audio";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export default function VoiceTagDetailPage() {
  const params = useParams<{ tagId: string }>();
  const router = useRouter();
  const t = useTranslations("dashboard.voiceTags");

  const tagId = params?.tagId ?? "";
  const { data: tagsRes, isLoading } = useVoiceTags();
  const tags = tagsRes?.success ? tagsRes.data ?? [] : [];
  const tag = tags.find((entry) => entry.id === tagId) ?? null;

  const { mutate: setDefaultMutate } = useSetDefaultVoiceTag();
  const { mutate: deleteMutate, isPending: deleting } = useDeleteVoiceTag();
  const [confirmDelete, setConfirmDelete] = useState(false);

  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  useEffect(() => {
    if (!tagId) return;
    let active = true;
    setPreviewLoading(true);
    previewVoiceTag({ tagId })
      .then((res) => {
        if (!active) return;
        if (res.success && res.data) setPreviewUrl(res.data.preSignedUrl);
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setPreviewLoading(false);
      });
    return () => {
      active = false;
    };
  }, [tagId]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
        Loading...
      </div>
    );
  }

  if (!tag) {
    return (
      <div className="flex flex-col items-center gap-4 p-12">
        <p className="text-sm text-neutral-500">Voice tag not found.</p>
        <Button variant="outline" onClick={() => router.push("/voice-tags")}>
          {t("pageBack")}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 font-sans">
      <Link
        href="/voice-tags"
        className="text-sm text-neutral-500 underline-offset-4 hover:underline"
      >
        &larr; {t("pageBack")}
      </Link>

      <header className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {tag.textContent}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">
          {tag.languageCode} - {tag.voiceName}
        </p>
        {tag.isDefault && (
          <span className="inline-flex w-fit items-center rounded-full border border-blue-300 bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300">
            {t("defaultBadge")}
          </span>
        )}
      </header>

      <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
        <h2 className="mb-3 text-sm font-semibold text-neutral-700 dark:text-neutral-300">
          {t("previewAudioTitle")}
        </h2>
        {previewLoading ? (
          <div className="flex items-center justify-center gap-3 p-6 text-sm text-neutral-500">
            <Spinner size="sm" /> Loading...
          </div>
        ) : previewUrl ? (
          <audio controls src={previewUrl} className="w-full" preload="metadata" />
        ) : (
          <p className="text-sm text-neutral-500">Preview unavailable.</p>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        {!tag.isDefault && (
          <Button
            variant="outline"
            onClick={() =>
              setDefaultMutate(
                { tagId: tag.id },
                {
                  onSuccess: () => toast.success(t("defaultBadge")),
                  onError: asApiError((err) => toast.error(err.message)),
                },
              )
            }
          >
            {t("setDefaultBtn")}
          </Button>
        )}
        <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
          {t("deleteBtn")}
        </Button>
      </div>

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t("deleteConfirmTitle")}</DialogTitle>
            <DialogDescription>{t("deleteConfirmDesc")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="-mx-4 -mb-4">
            <Button variant="ghost" disabled={deleting} onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleting}
              onClick={() =>
                deleteMutate(
                  { tagId: tag.id },
                  {
                    onSuccess: () => {
                      setConfirmDelete(false);
                      router.push("/voice-tags");
                    },
                    onError: asApiError((err) => toast.error(err.message)),
                  },
                )
              }
            >
              {deleting ? <Spinner size="sm" /> : t("deleteConfirmBtn")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
