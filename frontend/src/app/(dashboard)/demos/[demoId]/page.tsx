"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { asApiError } from "@/lib/api-client";
import {
  useDemoStatus,
  useRotateDemoKey,
} from "@/features/audio";
import type { DemoStatus } from "@/features/audio/types";

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 120000;

function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function Waveform({ values }: { values: number[] }) {
  if (!values.length) {
    return (
      <div className="flex h-32 items-center justify-center rounded-lg border border-dashed border-neutral-300 bg-neutral-50 text-xs text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900/40">
        <span>...</span>
      </div>
    );
  }
  const max = Math.max(...values, 1);
  const bars = values.slice(0, 200);
  return (
    <div className="flex h-32 items-end gap-0.5 overflow-hidden rounded-lg bg-neutral-100 p-2 dark:bg-neutral-900/40">
      {bars.map((v, i) => {
        const h = Math.max(2, Math.round((v / max) * 100));
        return (
          <span
            key={i}
            className="block w-1 rounded-sm bg-neutral-700 dark:bg-neutral-300"
            style={{ height: `${h}%` }}
            aria-hidden
          />
        );
      })}
    </div>
  );
}

function StatusBadge({ status }: { status: DemoStatus }) {
  const styles: Record<DemoStatus, string> = {
    PROCESSING: "border-yellow-300 bg-yellow-50 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300",
    ACTIVE: "border-green-300 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-300",
    FAILED: "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300",
    REVOKED: "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300",
    DELETED: "border-neutral-300 bg-neutral-100 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300",
  };
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${styles[status]}`}>
      {status}
    </span>
  );
}

export default function DemoDetailPage() {
  const params = useParams<{ demoId: string }>();
  const router = useRouter();
  const t = useTranslations("dashboard.demoDetail");

  const demoId = params?.demoId ?? "";

  const { data, isLoading, isError, refetch } = useDemoStatus({ demoId });

  const pollingStartedAt = useRef<number | null>(null);

  useEffect(() => {
    const status = data?.success ? data.data?.status : null;
    if (status !== "PROCESSING") {
      pollingStartedAt.current = null;
      return;
    }
    if (pollingStartedAt.current === null) {
      pollingStartedAt.current = Date.now();
    }
    const elapsed = Date.now() - (pollingStartedAt.current ?? Date.now());
    if (elapsed >= POLL_TIMEOUT_MS) {
      pollingStartedAt.current = null;
      return;
    }
    const id = setTimeout(() => {
      refetch();
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(id);
  }, [data, refetch]);

  const status = data?.success && data.data ? data.data.status : null;
  const errorMessage = data?.success && data.data ? data.data.errorMessage : null;

  const { mutate: rotateMutate, isPending: rotating } = useRotateDemoKey();
  const [rotateOpen, setRotateOpen] = useState(false);

  const handleRotate = () => {
    rotateMutate(
      { demoId },
      {
        onSuccess: (res) => {
          if (res.success) {
            toast.success(t("toastRotated"));
            setRotateOpen(false);
            refetch();
          }
        },
        onError: asApiError((err) => toast.error(err.message)),
      },
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
        {t("loading")}
      </div>
    );
  }

  if (isError || !data?.success || !data.data) {
    return (
      <div className="flex flex-col items-center gap-4 p-12 text-center">
        <p className="text-sm text-neutral-500">{t("notFound")}</p>
        <Button variant="outline" onClick={() => router.push("/demos")}>
          {t("back")}
        </Button>
      </div>
    );
  }

  const demo = data.data;

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Link href="/demos" className="text-sm text-neutral-500 underline-offset-4 hover:underline">
            &larr; {t("back")}
          </Link>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={demo.status} />
          {demo.status === "ACTIVE" && (
            <Button variant="destructive" size="sm" onClick={() => setRotateOpen(true)}>
              {t("rotateKey")}
            </Button>
          )}
        </div>
      </div>

      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {demo.title}
        </h1>
      </header>

      {demo.status === "PROCESSING" && (
        <div className="flex items-center gap-3 rounded-lg border border-yellow-300 bg-yellow-50 px-4 py-3 text-sm text-yellow-800 dark:border-yellow-800 dark:bg-yellow-950/30 dark:text-yellow-300">
          <Spinner size="sm" />
          <span>{t("toastPolling", { seconds: Math.floor(POLL_INTERVAL_MS / 1000) })}</span>
        </div>
      )}

      {demo.status === "FAILED" && errorMessage && (
        <div className="rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
          <strong className="block">{t("metadataErrorTitle")}</strong>
          <p className="mt-1 break-words">{errorMessage}</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-4 lg:col-span-2">
          <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
            <h2 className="mb-3 text-sm font-semibold text-neutral-700 dark:text-neutral-300">
              {demo.title}
            </h2>
            <Waveform values={demo.waveform} />
            {demo.status === "ACTIVE" && demo.hlsPlaylistUrl && (
              <audio
                controls
                className="mt-4 w-full"
                src={demo.hlsPlaylistUrl}
                preload="metadata"
              >
                {t("playerLabel")}
              </audio>
            )}
            {demo.status !== "ACTIVE" && (
              <div className="mt-4 flex h-10 items-center justify-center rounded-lg border border-dashed border-neutral-300 bg-neutral-50 text-xs text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900/40">
                {t("playerLabel")}
              </div>
            )}
          </div>
        </div>

        <aside className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-5 text-sm dark:border-neutral-800 dark:bg-black">
          <Row label={t("metadataFormat")} value={demo.format.toUpperCase()} />
          <Row label={t("metadataSampleRate")} value={`${demo.sampleRate} Hz`} />
          <Row label={t("metadataDuration")} value={formatDuration(demo.duration)} />
        </aside>
      </div>

      <Dialog open={rotateOpen} onOpenChange={setRotateOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t("rotateKeyConfirmTitle")}</DialogTitle>
            <DialogDescription>{t("rotateKeyConfirmDesc")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="-mx-4 -mb-4">
            <Button variant="ghost" disabled={rotating} onClick={() => setRotateOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={rotating} onClick={handleRotate}>
              {rotating ? <Spinner size="sm" /> : t("rotateKeyConfirmBtn")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="text-xs font-medium text-neutral-500 dark:text-neutral-400">{label}</span>
      <span className="text-sm font-semibold text-black dark:text-white">{value}</span>
    </div>
  );
}
