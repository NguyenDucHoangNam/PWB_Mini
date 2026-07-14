"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { asApiError } from "@/lib/api-client";
import {
  CreateVoiceTagModal,
  useDeleteVoiceTag,
  useSetDefaultVoiceTag,
  useVoiceTags,
} from "@/features/audio";
import type { VoiceTagResponse } from "@/features/audio/types";

function formatDate(iso: string) {
  return new Date(iso).toLocaleString();
}

export default function VoiceTagsPage() {
  const t = useTranslations("dashboard.voiceTags");
  const tCommon = useTranslations("dashboard.common");
  const tDashboard = useTranslations("dashboard");

  const { data: tagsRes, isLoading } = useVoiceTags();
  const tags: VoiceTagResponse[] = tagsRes?.success ? tagsRes.data ?? [] : [];

  const { mutate: setDefaultMutate, isPending: settingDefault } = useSetDefaultVoiceTag();
  const { mutate: deleteMutate, isPending: deleting } = useDeleteVoiceTag();
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<VoiceTagResponse | null>(null);

  const handleSetDefault = (tag: VoiceTagResponse) => {
    setDefaultMutate(
      { tagId: tag.id },
      {
        onSuccess: () => toast.success(tDashboard("voiceTagsDefaultCount")),
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    deleteMutate(
      { tagId: deleteTarget.id },
      {
        onSuccess: () => {
          toast.success(tDashboard("voiceTagsDefaultCount"));
          setDeleteTarget(null);
        },
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("title")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
        </div>
        <Button onClick={() => setCreateOpen(true)} className="self-start sm:self-auto">
          {t("createBtn")}
        </Button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
          <Spinner size="md" />
          {tCommon("loading")}
        </div>
      ) : tags.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-neutral-300 p-12 text-center dark:border-neutral-700">
          <h2 className="text-lg font-semibold text-black dark:text-white">{t("emptyTitle")}</h2>
          <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">{t("emptyDesc")}</p>
          <Button onClick={() => setCreateOpen(true)} className="mt-2">
            {t("emptyAction")}
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tags.map((tag) => (
            <article
              key={tag.id}
              className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex flex-col gap-1">
                  <p className="line-clamp-2 text-sm font-semibold text-black dark:text-white">
                    {tag.textContent}
                  </p>
                  <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    {t("cardLangVoice", { lang: tag.languageCode, voice: tag.voiceName })}
                  </p>
                  <p className="text-xs text-neutral-400">{formatDate(tag.createdAt)}</p>
                </div>
                {tag.isDefault && (
                  <span className="inline-flex shrink-0 items-center rounded-full border border-blue-300 bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-300">
                    {t("defaultBadge")}
                  </span>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-end gap-2">
                <Link href={`/voice-tags/${tag.id}`}>
                  <Button size="sm" variant="outline">
                    {t("previewBtn")}
                  </Button>
                </Link>
                {!tag.isDefault && (
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={settingDefault}
                    onClick={() => handleSetDefault(tag)}
                  >
                    {t("setDefaultBtn")}
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="destructive"
                  disabled={deleting}
                  onClick={() => setDeleteTarget(tag)}
                >
                  {t("deleteBtn")}
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}

      <CreateVoiceTagModal open={createOpen} onOpenChange={setCreateOpen} />

      <Dialog open={deleteTarget !== null} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        {deleteTarget ? (
          <DialogContent className="sm:max-w-sm">
            <DialogHeader>
              <DialogTitle>{t("deleteConfirmTitle")}</DialogTitle>
              <DialogDescription>{t("deleteConfirmDesc")}</DialogDescription>
            </DialogHeader>
            <DialogFooter className="-mx-4 -mb-4">
              <Button variant="ghost" disabled={deleting} onClick={() => setDeleteTarget(null)}>
                Cancel
              </Button>
              <Button variant="destructive" disabled={deleting} onClick={handleDelete}>
                {deleting ? <Spinner size="sm" /> : t("deleteConfirmBtn")}
              </Button>
            </DialogFooter>
          </DialogContent>
        ) : null}
      </Dialog>
    </div>
  );
}
