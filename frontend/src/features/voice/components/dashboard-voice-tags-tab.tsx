"use client";

import { useMemo } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Mic, Plus, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { VoiceTagCard } from "@/features/voice/components/voice-tag-card";
import { useListVoiceTags } from "@/features/voice/api/voice-tags";


function parsePage(value: string | null): number {
  const parsed = Number(value ?? "0");
  return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : 0;
}

export function DashboardVoiceTagsTab() {
  const t = useTranslations("voice.voiceTags");
  const tActions = useTranslations("voice.actions");
  const tList = useTranslations("voice.list");

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const page = useMemo(() => parsePage(searchParams.get("page")), [searchParams]);

  const updateQuery = (next: Record<string, string | null>) => {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(next)) {
      if (value === null || value === "") params.delete(key);
      else params.set(key, value);
    }
    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname);
  };

  const { data, isLoading, isFetching, isError, refetch } = useListVoiceTags({
    page,
    size: DEFAULT_PAGE_SIZE,
  });

  const items = data?.success && data.data ? data.data.content : [];
  const totalPages = data?.success && data.data ? data.data.totalPages : 0;

  const setPage = (newPage: number) => {
    updateQuery({ page: newPage === 0 ? null : String(newPage) });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
        {isLoading || isFetching ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
            <Spinner size="md" />
            {tList("loading")}
          </div>
        ) : isError ? (
          <div role="alert" className="flex flex-col items-center justify-center gap-3 p-12 text-center">
            <AlertCircle className="h-8 w-8 text-red-500" />
            <p className="text-sm font-medium text-red-600 dark:text-red-400">
              {t("errorLoad")}
            </p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              {t("retry")}
            </Button>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-4 p-12 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900">
              <Mic className="h-8 w-8 text-neutral-400" />
            </div>
            <div className="max-w-sm space-y-1">
              <h3 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
                {t("empty")}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                {t("emptyHint")}
              </p>
            </div>
            <Link href="/dashboard/voice-tags/new">
              <Button className="mt-2 flex items-center gap-2">
                <Plus className="h-4 w-4" />
                {tActions("create")}
              </Button>
            </Link>
          </div>
        ) : (
          <div className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((tag) => (
              <VoiceTagCard key={tag.id} voiceTag={tag} />
            ))}
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage(Math.max(0, page - 1))}
          >
            {tList("prev")}
          </Button>
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage(page + 1)}
          >
            {tList("next")}
          </Button>
        </div>
      )}
    </div>
  );
}
