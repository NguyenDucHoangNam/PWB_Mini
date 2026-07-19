"use client";

import { useMemo } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { VoiceTagCard } from "@/features/voice/components/voice-tag-card";
import { useListVoiceTags } from "@/features/voice/api/voice-tags";
import type { VoiceTagType } from "@/features/voice/types";

type TypeFilter = "ALL" | VoiceTagType;

const ALLOWED_TYPES: ReadonlySet<VoiceTagType> = new Set(["TTS", "UPLOADED"]);

function parseTypeFilter(value: string | null): TypeFilter {
  if (value && ALLOWED_TYPES.has(value as VoiceTagType)) {
    return value as TypeFilter;
  }
  return "ALL";
}

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

  const filter = useMemo(
    () => parseTypeFilter(searchParams.get("type")),
    [searchParams],
  );
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

  const { data, isLoading, isFetching, isError } = useListVoiceTags({
    page,
    size: DEFAULT_PAGE_SIZE,
    type: filter === "ALL" ? undefined : filter,
  });

  const items = data?.success && data.data ? data.data.content : [];
  const totalPages = data?.success && data.data ? data.data.totalPages : 0;

  const filters: { value: TypeFilter; label: string }[] = [
    { value: "ALL", label: tList("all") },
    { value: "TTS", label: t("type.TTS") },
    { value: "UPLOADED", label: t("type.UPLOADED") },
  ];

  const setFilter = (value: TypeFilter) => {
    updateQuery({ type: value === "ALL" ? null : value, page: null });
  };

  const setPage = (newPage: number) => {
    updateQuery({ page: newPage === 0 ? null : String(newPage) });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        {filters.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setFilter(opt.value)}
            aria-pressed={filter === opt.value}
            className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
              filter === opt.value
                ? "border-black bg-black text-white dark:border-white dark:bg-white dark:text-black"
                : "border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50 dark:border-neutral-800 dark:bg-black dark:text-neutral-300 dark:hover:bg-neutral-900"
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div className="rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-black">
        {isLoading || isFetching ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
            <Spinner size="md" />
            {tList("loading")}
          </div>
        ) : isError ? (
          <div
            role="alert"
            className="p-12 text-center text-sm text-red-600 dark:text-red-400"
          >
            {tList("loading")}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 p-12 text-center">
            <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("empty")}</p>
            <Link href="/dashboard/voice-tags/new">
              <Button className="mt-2">{tActions("create")}</Button>
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
