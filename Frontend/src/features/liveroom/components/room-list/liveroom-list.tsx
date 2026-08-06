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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-black md:text-3xl dark:text-white">
            {t("title")}
          </h1>
          <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
            {t("subtitle")}
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Link href="/dashboard/liveroom/join">
            <Button variant="outline" className="h-11 w-full md:h-9 sm:w-auto">
              <KeyRound className="size-4" />
              {t("joinByCode")}
            </Button>
          </Link>
          <Link href="/dashboard/liveroom/new">
            <Button className="h-11 w-full md:h-9 sm:w-auto">
              <Plus className="size-4" />
              {t("create")}
            </Button>
          </Link>
        </div>
      </div>

      <SearchInput
        value={keyword}
        onValueChange={setKeyword}
        loading={searching && isFetching}
        placeholder={t("searchPlaceholder")}
        clearLabel={t("clearSearch")}
        aria-label={t("searchPlaceholder")}
      />

      <div
        role="tablist"
        aria-label={t("title")}
        className="flex gap-1 rounded-lg border border-neutral-200 bg-neutral-50 p-1 dark:border-neutral-800 dark:bg-neutral-900"
      >
        {TABS.map((tab) => (
          <button
            key={tab.value}
            role="tab"
            type="button"
            aria-selected={status === tab.value}
            onClick={() => selectTab(tab.value)}
            className={`h-11 flex-1 rounded-md px-3 text-sm font-medium transition-colors md:h-9 ${
              status === tab.value
                ? "bg-white text-black shadow-sm dark:bg-black dark:text-white"
                : "text-neutral-500 hover:text-black dark:text-neutral-400 dark:hover:text-white"
            }`}
          >
            {t(tab.labelKey)}
          </button>
        ))}
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