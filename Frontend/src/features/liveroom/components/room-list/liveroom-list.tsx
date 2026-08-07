"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { KeyRound, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Pagination } from "@/components/ui/pagination";
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

  const selectTab = (next: RoomStatus) => {
    setStatus(next);
    setPage(0);
  };

  return (
    <div className="flex flex-1 flex-col gap-5 sm:gap-6">
      <header className="flex flex-col gap-4 border-b border-border pb-4 sm:flex-row sm:items-center sm:justify-between sm:gap-5 sm:pb-5">
        <div className="flex items-start gap-3">
          <div className="hidden h-12 w-1 shrink-0 rounded-full bg-foreground/80 sm:block" aria-hidden="true" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              {t("title")}
            </h1>
            <p className="text-xs uppercase tracking-widest text-muted-foreground/70 sm:text-[11px]">
              {t("subtitle")}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/dashboard/liveroom/join" className="flex-1 sm:flex-none">
            <Button variant="outline" className="h-10 w-full font-semibold sm:h-9 sm:w-auto">
              <KeyRound className="size-4" />
              {t("joinByCode")}
            </Button>
          </Link>
          <Link href="/dashboard/liveroom/new" className="flex-1 sm:flex-none">
            <Button className="h-10 w-full font-semibold sm:h-9 sm:w-auto">
              <Plus className="size-4" />
              {t("create")}
            </Button>
          </Link>
        </div>
      </header>

      <div className="flex flex-1 flex-col gap-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="sm:max-w-xs sm:flex-1">
            <SearchInput
              value={keyword}
              onValueChange={setKeyword}
              loading={searching && isFetching}
              placeholder={t("searchPlaceholder")}
              clearLabel={t("clearSearch")}
              aria-label={t("searchPlaceholder")}
              className="h-11 sm:h-9"
            />
          </div>

          <div
            role="tablist"
            aria-label={t("title")}
            className="flex h-11 shrink-0 items-center gap-1 rounded-lg border border-border bg-muted p-1 sm:h-9 sm:w-auto"
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
                  className={`key-press flex h-9 flex-1 items-center justify-center rounded-md px-4 text-xs font-semibold tracking-wide beat-16th transition-colors ease-hammer sm:h-7 sm:flex-none ${
                    isActive
                      ? "border border-border bg-card text-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {t(tab.labelKey)}
                </button>
              );
            })}
          </div>
        </div>

        {isPending ? (
          <div className="flex flex-1 items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Spinner size="sm" />
            {t("loading")}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-border bg-card p-8 text-center">
            <p className="text-sm text-muted-foreground">{t("errorLoad")}</p>
            <Button variant="outline" onClick={() => refetch()}>
              {t("retry")}
            </Button>
          </div>
        ) : rooms.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 rounded-xl border border-border bg-card p-8 text-center md:p-12">
            <h2 className="text-lg font-semibold text-foreground">
              {searching ? t("noResults") : t("empty")}
            </h2>
            <p className="max-w-md text-sm text-muted-foreground">
              {searching ? t("noResultsHint", { query: debouncedKeyword }) : t("emptyHint")}
            </p>
            {searching ? (
              <Button variant="outline" onClick={() => setKeyword("")}>
                {t("clearSearch")}
              </Button>
            ) : null}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-5 xl:grid-cols-3">
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

        <Pagination
          page={page}
          totalPages={data?.data?.totalPages ?? 0}
          totalElements={data?.data?.totalElements}
          pageSize={DEFAULT_PAGE_SIZE}
          onPageChange={setPage}
        />
      </div>

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