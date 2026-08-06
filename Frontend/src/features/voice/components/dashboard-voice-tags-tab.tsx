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

  // Local field, debounced URL: typing stays instant while the address bar still describes the result.
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

  /**
   * Deleting the last tag on the last page leaves the URL pointing past the end, where the list renders
   * the "no voice tags yet" state and the pager hides itself — nothing is left to click back with. The
   * response echoes the page it describes, so a stale keepPreviousData payload cannot trigger this.
   */
  useEffect(() => {
    if (!pageData || pageData.page !== page || page === 0 || page < totalPages) return;
    router.replace(buildHref(totalPages - 1));
  }, [pageData, page, totalPages, router, buildHref]);

  const setPage = (newPage: number) => {
    router.push(buildHref(newPage));
  };

  /**
   * A changed keyword makes the old page number meaningless, so it is dropped along with it.
   *
   * `replace`, not `push`: typing one word would otherwise leave a history entry per pause, so Back
   * would walk letter by letter back out of the search instead of leaving the page.
   */
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

  return (
    <div className="flex flex-col gap-4">
      <SearchInput
        value={keyword}
        onValueChange={setKeyword}
        loading={searching && isFetching}
        placeholder={tList("searchVoiceTagsPlaceholder")}
        clearLabel={tList("clearSearch")}
        aria-label={tList("searchVoiceTagsPlaceholder")}
      />

      <div
        aria-busy={isFetching}
        className={`rounded-xl border border-neutral-200 bg-white transition-opacity dark:border-neutral-800 dark:bg-black ${
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
