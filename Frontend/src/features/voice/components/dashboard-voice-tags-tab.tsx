"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Mic, Plus, SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { VoiceTagCard } from "@/features/voice/components/voice-tag-card";
import {
  LibraryCardsSkeleton,
  LibraryEmptyState,
  LibraryErrorState,
  LibraryPagination,
  LibraryPanel,
} from "@/features/voice/components/library-states";
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
    <div className="flex flex-1 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="sm:max-w-sm sm:flex-1">
          <SearchInput
            value={keyword}
            onValueChange={setKeyword}
            loading={searching && isFetching}
            placeholder={tList("searchVoiceTagsPlaceholder")}
            clearLabel={tList("clearSearch")}
            aria-label={tList("searchVoiceTagsPlaceholder")}
            className="h-11 sm:h-9"
          />
        </div>

        <div className="flex items-center gap-3 sm:ml-auto">
          {totalElements > 0 && (
            <p className="hidden text-xs text-muted-foreground sm:block">
              {tList("voiceTagCount", { count: totalElements })}
            </p>
          )}
          <Link href="/dashboard/voice-tags/new" className="shrink-0">
            <Button size="lg" className="h-11 gap-2 font-semibold sm:h-9">
              <Plus className="size-4" aria-hidden="true" />
              {tActions("create")}
            </Button>
          </Link>
        </div>
      </div>


      {isLoading ? (
        <LibraryCardsSkeleton />
      ) : isError ? (
        <LibraryPanel>
          <LibraryErrorState
            message={t("errorLoad")}
            retryLabel={t("retry")}
            onRetry={() => refetch()}
          />
        </LibraryPanel>
      ) : items.length === 0 && searching ? (
        <LibraryPanel>
          <LibraryEmptyState
            icon={SearchX}
            title={tList("noResults")}
            hint={tList("noResultsHint", { query: debouncedKeyword })}
            action={
              <Button variant="outline" size="lg" onClick={() => setKeyword("")}>
                {tList("clearSearch")}
              </Button>
            }
          />
        </LibraryPanel>
      ) : items.length === 0 ? (
        <LibraryPanel>
          <LibraryEmptyState
            icon={Mic}
            title={t("empty")}
            hint={t("emptyHint")}
            action={
              <Link href="/dashboard/voice-tags/new">
                <Button size="lg" className="gap-2">
                  <Plus className="size-4" aria-hidden="true" />
                  {tActions("create")}
                </Button>
              </Link>
            }
          />
        </LibraryPanel>
      ) : (
        <ul
          aria-busy={isFetching}
          className={`grid gap-3 beat-8th transition-opacity sm:grid-cols-2 sm:gap-4 xl:grid-cols-3 ${
            isFetching ? "opacity-70" : ""
          }`}
        >
          {items.map((tag) => (
            <li key={tag.id}>
              <VoiceTagCard voiceTag={tag} />
            </li>
          ))}
        </ul>
      )}

      <LibraryPagination
        page={page}
        totalPages={totalPages}
        rangeFrom={rangeFrom}
        rangeTo={rangeTo}
        totalElements={totalElements}
        onPageChange={setPage}
      />
    </div>
  );
}
