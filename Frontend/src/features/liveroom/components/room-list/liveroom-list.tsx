"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { KeyRound, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { useListRooms, useSearchRooms } from "../../api/rooms";
import { EndRoomDialog } from "./end-room-dialog";
import { ReopenRoomDialog } from "./reopen-room-dialog";
import { RoomCard } from "./room-card";
import type { Room, RoomStatus } from "../../types";

const TABS: { value: RoomStatus; labelKey: string }[] = [
  { value: "ACTIVE", labelKey: "tabActive" },
  { value: "ENDED", labelKey: "tabEnded" },
];

const SEARCH_DEBOUNCE_MS = 250;

export function LiveroomList() {
  const t = useTranslations("liveroom.list");
  const [status, setStatus] = useState<RoomStatus>("ACTIVE");
  const [page, setPage] = useState(0);
  const [endTarget, setEndTarget] = useState<Room | null>(null);
  const [reopenTarget, setReopenTarget] = useState<Room | null>(null);
  const [keyword, setKeyword] = useState("");

  const debouncedKeyword = useDebouncedValue(keyword, SEARCH_DEBOUNCE_MS);
  const searching = debouncedKeyword.trim().length > 0;




  const [pagedKeyword, setPagedKeyword] = useState(debouncedKeyword);
  if (pagedKeyword !== debouncedKeyword) {
    setPagedKeyword(debouncedKeyword);
    setPage(0);
  }

  const listQuery = useListRooms({
    status,
    page,
    size: DEFAULT_PAGE_SIZE,
    queryConfig: { enabled: !searching },
  });

  const searchQuery = useSearchRooms({
    q: debouncedKeyword,
    status,
    page,
    size: DEFAULT_PAGE_SIZE,
    queryConfig: { enabled: searching },
  });

  const { data, isPending, isFetching, isError, refetch } = searching ? searchQuery : listQuery;

  const rooms = data?.data?.content ?? [];
  const isLast = data?.data?.last ?? true;

  const selectTab = (next: RoomStatus) => {
    setStatus(next);
    setPage(0);
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between border-b border-neutral-200 pb-4 dark:border-neutral-800">
        <div className="flex flex-col">
          <div className="flex items-center gap-2">
            <span className="h-5 w-1 rounded-full bg-black dark:bg-white" aria-hidden="true" />
            <h1 className="text-2xl font-bold tracking-tight text-neutral-900 md:text-3xl dark:text-neutral-100">
              {t("title")}
            </h1>
          </div>
          <p className="mt-0.5 text-xs sm:text-sm text-neutral-500 dark:text-neutral-400 ml-3">
            {t("subtitle")}
          </p>
        </div>
        <div className="flex flex-col gap-2.5 sm:flex-row sm:items-center">
          <div className="w-full sm:w-64 md:w-72 shrink-0">
            <SearchInput
              value={keyword}
              onValueChange={setKeyword}
              loading={searching && isFetching}
              placeholder={t("searchPlaceholder")}
              clearLabel={t("clearSearch")}
              aria-label={t("searchPlaceholder")}
            />
          </div>
          <Link href="/dashboard/liveroom/join">
            <Button
              variant="outline"
              className="h-9 min-h-[44px] sm:min-h-0 w-full sm:w-auto border-neutral-300 font-semibold text-neutral-800 hover:bg-neutral-100 dark:border-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-800 active:translate-y-[1px] transition-all"
            >
              <KeyRound className="size-4" />
              {t("joinByCode")}
            </Button>
          </Link>
          <Link href="/dashboard/liveroom/new">
            <Button className="h-9 min-h-[44px] sm:min-h-0 w-full sm:w-auto bg-black font-semibold text-white hover:bg-neutral-800 dark:bg-white dark:text-black dark:hover:bg-neutral-200 active:translate-y-[1px] transition-all">
              <Plus className="size-4" />
              {t("create")}
            </Button>
          </Link>
        </div>
      </div>

      <div
        role="tablist"
        aria-label={t("title")}
        className="flex h-10 items-center gap-1 rounded-t-lg bg-neutral-100 p-1 dark:bg-neutral-900 border border-b-0 border-neutral-200 dark:border-neutral-800"
      >
        {TABS.map((tab) => {
          const isActive = status === tab.value;
          return (
            <button
              key={tab.value}
              role="tab"
              type="button"
              aria-selected={isActive}
              onClick={() => selectTab(tab.value)}
              className={`relative flex h-8 flex-1 items-center justify-center rounded-md px-3 text-xs font-semibold tracking-wide transition-all active:translate-y-[1px] ${
                isActive
                  ? "bg-white text-black shadow-xs dark:bg-black dark:text-white border border-neutral-200 dark:border-neutral-800"
                  : "text-neutral-500 hover:text-black dark:text-neutral-400 dark:hover:text-white"
              }`}
            >
              {isActive && (
                <span
                  className="absolute -bottom-1 left-1/2 h-[3px] w-5 -translate-x-1/2 rounded-full bg-black dark:bg-white"
                  aria-hidden="true"
                />
              )}
              {t(tab.labelKey)}
            </button>
          );
        })}
      </div>

      {isPending ? (
        <div className="flex items-center justify-center gap-2 py-16 text-sm text-neutral-500 dark:text-neutral-400">
          <Spinner size="sm" />
          {t("loading")}
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-neutral-200 bg-white p-8 text-center dark:border-neutral-800 dark:bg-black">
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("errorLoad")}</p>
          <Button variant="outline" onClick={() => refetch()}>
            {t("retry")}
          </Button>
        </div>
      ) : rooms.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-neutral-200 bg-white p-8 text-center md:p-12 dark:border-neutral-800 dark:bg-black">
          <h2 className="text-lg font-semibold text-black dark:text-white">
            {searching ? t("noResults") : t("empty")}
          </h2>
          <p className="max-w-md text-sm text-neutral-500 dark:text-neutral-400">
            {searching ? t("noResultsHint", { query: debouncedKeyword }) : t("emptyHint")}
          </p>
          {searching ? (
            <Button variant="outline" onClick={() => setKeyword("")}>
              {t("clearSearch")}
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-6 xl:grid-cols-3">
          {rooms.map((room) => (
            <RoomCard
              key={room.id}
              room={room}
              onEnd={setEndTarget}
              onReopen={setReopenTarget}
            />
          ))}
        </div>
      )}

      {rooms.length > 0 ? (
        <div className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            className="h-11 md:h-9"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {t("prev")}
          </Button>
          <Button
            variant="outline"
            className="h-11 md:h-9"
            disabled={isLast}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("next")}
          </Button>
        </div>
      ) : null}

      <EndRoomDialog
        room={endTarget}
        open={Boolean(endTarget)}
        onOpenChange={(open) => !open && setEndTarget(null)}
      />
      <ReopenRoomDialog
        room={reopenTarget}
        open={Boolean(reopenTarget)}
        onOpenChange={(open) => !open && setReopenTarget(null)}
      />
    </div>
  );
}