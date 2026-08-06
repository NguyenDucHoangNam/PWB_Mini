"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Mic, Plus, AlertCircle, SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { VoiceTagCard } from "@/features/voice/components/voice-tag-card";
import { useListVoiceTags, useSearchVoiceTags } from "@/features/voice/api/voice-tags";

const SEARCH_DEBOUNCE_MS = 250;

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
  const queryFromUrl = searchParams.get("q") ?? "";

  const [keyword, setKeyword] = useState(queryFromUrl);
  const debouncedKeyword = useDebouncedValue(keyword, SEARCH_DEBOUNCE_MS);
  const searching = debouncedKeyword.trim().length > 0;

  const buildHref = useCallback(
    (nextPage: number) => {
      const params = new URLSearchParams(searchParams.toString());
      if (nextPage <= 0) params.delete("page");
      else params.set("page", String(nextPage));
      const query = params.toString();
      return query ? `${pathname}?${query}` : pathname;
    },
    [pathname, searchParams],
  );

  const listQuery = useListVoiceTags({
    page,
    size: DEFAULT_PAGE_SIZE,
    queryConfig: { enabled: !searching },
  });

  const searchQuery = useSearchVoiceTags({
    page,
    size: DEFAULT_PAGE_SIZE,
    q: debouncedKeyword,
    queryConfig: { enabled: searching },
  });

  const { data, isLoading, isFetching, isError, refetch } = searching ? searchQuery : listQuery;

  const pageData = data?.success && data.data ? data.data : null;
  const items = pageData ? pageData.content : [];
  const totalPages = pageData ? pageData.totalPages : 0;
  const totalElements = pageData ? pageData.totalElements : 0;

  useEffect(() => {
    if (!pageData || pageData.page !== page || page === 0 || page < totalPages) return;
    router.replace(buildHref(totalPages - 1));
  }, [pageData, page, totalPages, router, buildHref]);

  const setPage = (newPage: number) => {
    router.push(buildHref(newPage));
  };

  useEffect(() => {
    if (debouncedKeyword === queryFromUrl) return;
    const params = new URLSearchParams(searchParams.toString());
    params.delete("page");
    if (debouncedKeyword) params.set("q", debouncedKeyword);
    else params.delete("q");
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword, queryFromUrl]);

  const rangeFrom = page * DEFAULT_PAGE_SIZE + 1;
  const rangeTo = Math.min(rangeFrom + items.length - 1, totalElements);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex-1">
        <SearchInput
          value={keyword}
          onValueChange={setKeyword}
          loading={searching && isFetching}
          placeholder={tList("searchVoiceTagsPlaceholder")}
          clearLabel={tList("clearSearch")}
          aria-label={tList("searchVoiceTagsPlaceholder")}
        />
      </div>

      <div
        aria-busy={isFetching}
        className={`overflow-hidden rounded-xl border border-neutral-200 bg-white transition-opacity dark:border-neutral-800 dark:bg-black ${
          isFetching && !isLoading ? "opacity-60" : ""
        }`}
      >
        {isLoading ? (
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
        ) : items.length === 0 && searching ? (
          <div className="flex flex-col items-center justify-center gap-4 p-12 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-900">
              <SearchX className="h-8 w-8 text-neutral-400" />
            </div>
            <div className="max-w-sm space-y-1">
              <h3 className="text-base font-semibold text-neutral-900 dark:text-neutral-100">
                {tList("noResults")}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400">
                {tList("noResultsHint", { query: debouncedKeyword })}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => setKeyword("")}>
              {tList("clearSearch")}
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
          <div className="divide-y divide-neutral-100 dark:divide-neutral-800/50">
            {items.map((tag, i) => {
              const isOdd = i % 2 === 0;
              return (
                <div
                  key={tag.id}
                  className={`relative ${
                    isOdd
                      ? "bg-white dark:bg-black"
                      : "bg-neutral-50/60 dark:bg-neutral-950/60"
                  }`}
                >
                  <div
                    className={`absolute left-0 top-0 h-full w-[3px] ${
                      isOdd
                        ? "bg-neutral-900 dark:bg-neutral-100"
                        : "bg-transparent"
                    }`}
                    aria-hidden="true"
                  />
                  <div className="px-4 py-3">
                    <VoiceTagCard voiceTag={tag} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-xs text-neutral-500 dark:text-neutral-400">
            {tList("showingRange", { from: rangeFrom, to: rangeTo, total: totalElements })}
          </span>
          <div className="flex items-center gap-2">
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
        </div>
      )}
    </div>
  );
}
