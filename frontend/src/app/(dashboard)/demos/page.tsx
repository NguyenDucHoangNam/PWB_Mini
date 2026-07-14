"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import {
  useDemos,
  useVoiceTags,
  UploadDemoModal,
  DistributeDemoModal,
} from "@/features/audio";
import type { DemoListItem, DemoStatus } from "@/features/audio/types";

type StatusFilter = "ALL" | DemoStatus;

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatDuration(seconds: number | null) {
  if (seconds === null || Number.isNaN(seconds)) return "-";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function formatDate(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString();
}

function StatusBadge({ status }: { status: DemoStatus }) {
  const t = useTranslations("dashboard.demos");
  const styles: Record<DemoStatus, string> = {
    PROCESSING: "border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300",
    ACTIVE: "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
    FAILED: "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300",
    REVOKED: "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300",
    DELETED: "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300",
  };
  const label = status === "PROCESSING" ? t("statusProcessing")
    : status === "ACTIVE" ? t("statusActive")
    : status === "FAILED" ? t("statusFailed")
    : status === "REVOKED" ? t("statusRevoked")
    : t("statusDeleted");

  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${styles[status]}`}>
      {label}
    </span>
  );
}

const STATUS_LABELS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "filterAll" },
  { value: "ACTIVE", label: "filterActive" },
  { value: "PROCESSING", label: "filterProcessing" },
  { value: "FAILED", label: "filterFailed" },
];

export default function DemosPage() {
  const t = useTranslations("dashboard.demos");
  const tDashboard = useTranslations("dashboard");
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [distributeDemoId, setDistributeDemoId] = useState<string | null>(null);

  const { data: demosRes, isLoading, isFetching } = useDemos({
    page,
    size: DEFAULT_PAGE_SIZE,
  });

  const { data: voiceTagsRes } = useVoiceTags();
  const voiceTags = voiceTagsRes?.success ? voiceTagsRes.data ?? [] : [];
  const tagNameById = new Map(voiceTags.map((tag) => [tag.id, tag.textContent]));

  const allDemos: DemoListItem[] = demosRes?.success && demosRes.data ? demosRes.data.content : [];
  const totalPages = demosRes?.success && demosRes.data ? demosRes.data.totalPages : 0;
  const totalElements = demosRes?.success && demosRes.data ? demosRes.data.totalElements : 0;

  const filteredDemos = filter === "ALL" ? allDemos : allDemos.filter((d) => d.status === filter);

  const distributeDemo = filteredDemos.find((d) => d.demoId === distributeDemoId) ?? null;

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("title")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
        </div>
        <Button onClick={() => setUploadOpen(true)} className="self-start sm:self-auto">
          {t("uploadBtn")}
        </Button>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUS_LABELS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setFilter(opt.value)}
            className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
              filter === opt.value
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50 dark:border-neutral-800 dark:bg-black dark:text-neutral-300 dark:hover:bg-neutral-900"
            }`}
          >
            {t(opt.label)}
          </button>
        ))}
      </div>

      <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
        {isLoading || isFetching ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
            <Spinner size="md" />
            {tDashboard("common.loading")}
          </div>
        ) : filteredDemos.length === 0 ? (
          <div className="flex flex-col items-center gap-3 p-12 text-center">
            <h2 className="text-lg font-semibold text-black dark:text-white">
              {t("emptyTitle")}
            </h2>
            <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
              {t("emptyDesc")}
            </p>
            <Button onClick={() => setUploadOpen(true)} className="mt-2">
              {t("emptyAction")}
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900/40">
                  <th className="px-4 py-3 font-medium">{t("colTitle")}</th>
                  <th className="px-4 py-3 font-medium">{t("colStatus")}</th>
                  <th className="px-4 py-3 font-medium">{t("colDuration")}</th>
                  <th className="px-4 py-3 font-medium">{t("colSize")}</th>
                  <th className="px-4 py-3 font-medium">{t("colFormat")}</th>
                  <th className="px-4 py-3 font-medium">{t("colCreatedAt")}</th>
                  <th className="px-4 py-3 font-medium text-right">{t("colActions")}</th>
                </tr>
              </thead>
              <tbody>
                {filteredDemos.map((demo) => (
                  <tr
                    key={demo.demoId}
                    className="border-b border-neutral-200 last:border-b-0 hover:bg-neutral-50/60 dark:border-neutral-800 dark:hover:bg-neutral-900/30"
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-black dark:text-white">{demo.title}</div>
                      {demo.voiceTagId && (
                        <div className="text-xs text-neutral-500">
                          {tagNameById.get(demo.voiceTagId)?.slice(0, 32) ?? demo.voiceTagId.slice(0, 8)}
                        </div>
                      )}
                      {demo.errorMessage && (
                        <div className="mt-1 text-xs text-red-600 dark:text-red-400">
                          {demo.errorMessage}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={demo.status} />
                    </td>
                    <td className="px-4 py-3 tabular-nums">{formatDuration(demo.duration)}</td>
                    <td className="px-4 py-3 tabular-nums">{formatBytes(demo.fileSize)}</td>
                    <td className="px-4 py-3 uppercase">{demo.format ?? "-"}</td>
                    <td className="px-4 py-3 text-neutral-500">{formatDate(demo.createdAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => router.push(`/demos/${demo.demoId}`)}
                        >
                          {t("viewDetail")}
                        </Button>
                        {demo.status === "ACTIVE" && (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setDistributeDemoId(demo.demoId)}
                          >
                            {t("shareBtn")}
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-neutral-500 dark:text-neutral-400">
            {t("pageOf", { page: page + 1, total: totalPages || 1 })}
            {" - "}
            {totalElements} {tDashboard("demosTotalLabel").toLowerCase()}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              {t("prev")}
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              {t("next")}
            </Button>
          </div>
        </div>
      )}

      <UploadDemoModal
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        onSuccess={() => setPage(0)}
      />

      {distributeDemo && (
        <DistributeDemoModal
          open={distributeDemoId !== null}
          onOpenChange={(o) => !o && setDistributeDemoId(null)}
          demoId={distributeDemo.demoId}
          demoTitle={distributeDemo.title}
        />
      )}
    </div>
  );
}
