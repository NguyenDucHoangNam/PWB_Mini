"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
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
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import {
  DistributeDemoModal,
  useDistributions,
  useDemos,
  useRevokeAllDistributions,
  useRevokeDistribution,
} from "@/features/audio";

function formatDate(iso: string) {
  return new Date(iso).toLocaleString();
}

export default function DistributionsDetailPage() {
  const params = useParams<{ demoId: string }>();
  const router = useRouter();
  const t = useTranslations("dashboard.distributions");

  const demoId = params?.demoId ?? "";
  const [page, setPage] = useState(0);
  const [includeRevoked, setIncludeRevoked] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [confirmRevokeAll, setConfirmRevokeAll] = useState(false);

  const { data: demosRes } = useDemos({ page: 0, size: 100 });
  const demo = demosRes?.success && demosRes.data
    ? demosRes.data.content.find((d) => d.demoId === demoId) ?? null
    : null;

  const { data: distRes, isLoading, refetch } = useDistributions({
    demoId,
    page,
    size: DEFAULT_PAGE_SIZE,
    includeRevoked,
  });

  const { mutate: revokeMutate, isPending: revoking } = useRevokeDistribution();
  const { mutate: revokeAllMutate, isPending: revokingAll } = useRevokeAllDistributions();

  const distributions =
    distRes?.success && distRes.data ? distRes.data.content : [];
  const totalPages = distRes?.success && distRes.data ? distRes.data.totalPages : 0;

  const handleCopy = async (link: string) => {
    try {
      await navigator.clipboard.writeText(link);
      toast.success(t("copiedBtn"));
    } catch {
      toast.error("Copy failed");
    }
  };

  const handleRevoke = (distributionId: string) => {
    revokeMutate(
      { demoId, distributionId },
      {
        onSuccess: () => {
          toast.success(t("revokeBtn"));
          refetch();
        },
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  const handleRevokeAll = () => {
    revokeAllMutate(
      { demoId },
      {
        onSuccess: (res) => {
          if (res.success) {
            toast.success(t("revokeAllConfirmBtn") + ": " + (res.data ?? 0));
            setConfirmRevokeAll(false);
            refetch();
          }
        },
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  return (
    <div className="flex flex-col gap-6 font-sans">
      <Link
        href="/distributions"
        className="text-sm text-neutral-500 underline-offset-4 hover:underline"
      >
        &larr; {t("pageBack")}
      </Link>

      <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {demo?.title ?? t("detailTitle")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("detailSubtitle")}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => setShareOpen(true)}>{t("newShareBtn")}</Button>
          {distributions.length > 0 && (
            <Button variant="destructive" onClick={() => setConfirmRevokeAll(true)}>
              {t("revokeAllBtn")}
            </Button>
          )}
        </div>
      </header>

      <div className="flex items-center gap-2">
        <Checkbox
          id="include-revoked"
          checked={includeRevoked}
          onCheckedChange={(checked: boolean) => {
            setIncludeRevoked(checked);
            setPage(0);
          }}
        />
        <Label htmlFor="include-revoked">{t("includeRevoked")}</Label>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
          <Spinner size="md" />
          Loading...
        </div>
      ) : distributions.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-neutral-300 p-12 text-center dark:border-neutral-700">
          <p className="max-w-md text-sm text-neutral-500">{t("emptyRecipients")}</p>
          <Button onClick={() => setShareOpen(true)}>{t("newShareBtn")}</Button>
        </div>
      ) : (
        <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900/40">
                  <th className="px-4 py-3 font-medium">{t("colRecipient")}</th>
                  <th className="px-4 py-3 font-medium">{t("colShareLink")}</th>
                  <th className="px-4 py-3 font-medium">{t("colPlayCount")}</th>
                  <th className="px-4 py-3 font-medium">{t("colCreatedAt")}</th>
                  <th className="px-4 py-3 font-medium">{t("colStatus")}</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {distributions.map((distribution) => {
                  const link = `${typeof window !== "undefined" ? window.location.origin : ""}/shared/${distribution.shareToken}`;
                  return (
                    <tr
                      key={distribution.distributionId}
                      className="border-b border-neutral-200 last:border-b-0 hover:bg-neutral-50/60 dark:border-neutral-800 dark:hover:bg-neutral-900/30"
                    >
                      <td className="px-4 py-3">{distribution.recipientEmail}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <code className="max-w-[14rem] truncate rounded bg-neutral-100 px-1.5 py-0.5 text-xs dark:bg-neutral-900">
                            {distribution.shareToken}
                          </code>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => handleCopy(link)}
                          >
                            {t("copyBtn")}
                          </Button>
                        </div>
                      </td>
                      <td className="px-4 py-3 tabular-nums">{distribution.playCount}</td>
                      <td className="px-4 py-3 text-neutral-500">{formatDate(distribution.createdAt)}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${
                            distribution.revoked
                              ? "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300"
                              : "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300"
                          }`}
                        >
                          {distribution.revoked ? t("revokedBadge") : t("activeBadge")}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {!distribution.revoked && (
                          <Button
                            size="sm"
                            variant="destructive"
                            disabled={revoking}
                            onClick={() => handleRevoke(distribution.distributionId)}
                          >
                            {t("revokeBtn")}
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-end gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Prev
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}

      <DistributeDemoModal
        open={shareOpen}
        onOpenChange={setShareOpen}
        demoId={demoId}
        demoTitle={demo?.title}
      />

      <Dialog open={confirmRevokeAll} onOpenChange={setConfirmRevokeAll}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t("revokeAllConfirmTitle")}</DialogTitle>
            <DialogDescription>{t("revokeAllConfirmDesc")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="-mx-4 -mb-4">
            <Button variant="ghost" disabled={revokingAll} onClick={() => setConfirmRevokeAll(false)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={revokingAll} onClick={handleRevokeAll}>
              {revokingAll ? <Spinner size="sm" /> : t("revokeAllConfirmBtn")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Button variant="ghost" onClick={() => router.push("/distributions")}>
        {t("pageBack")}
      </Button>
    </div>
  );
}
