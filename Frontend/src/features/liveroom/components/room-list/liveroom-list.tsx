"use client";

import { useState } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { KeyRound, Plus, Radio } from "lucide-react";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Spinner } from "@/components/ui/spinner";
import { PageHeader } from "@/components/layout/page-header";
import {
  NEU_FOCUS,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
  NeuPanel,
  neuButton,
} from "@/components/ui/neu";
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
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        title={t("title")}
        subtitle={t("subtitle")}
        icon={Radio}
        actions={
          <>
            <Link href="/dashboard/liveroom/join" className={neuButton()}>
              <KeyRound className="size-4" aria-hidden="true" />
              {t("joinByCode")}
            </Link>
            <Link href="/dashboard/liveroom/new" className={neuButton({ variant: "primary" })}>
              <Plus className="size-4" aria-hidden="true" />
              {t("create")}
            </Link>
          </>
        }
      />

      <div className="flex flex-1 flex-col gap-5">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="sm:max-w-xs sm:flex-1">
            <SearchInput
              variant="neu"
              value={keyword}
              onValueChange={setKeyword}
              loading={searching && isFetching}
              placeholder={t("searchPlaceholder")}
              clearLabel={t("clearSearch")}
              aria-label={t("searchPlaceholder")}
            />
          </div>

          <div
            role="tablist"
            aria-label={t("title")}
            className="neu-pressed flex shrink-0 items-center gap-2 rounded-2xl border-none p-2"
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
                  className={`flex h-9 flex-1 items-center justify-center gap-2 rounded-xl border-none px-5 text-xs font-bold tracking-wide transition-all sm:flex-none ${NEU_FOCUS} ${
                    isActive
                      ? "neu-raised-sm text-indigo-600 dark:text-indigo-400"
                      : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
                  }`}
                >
                  {t(tab.labelKey)}
                  {isActive ? (
                    <span
                      className="size-1.5 rounded-full bg-indigo-600 dark:bg-indigo-400"
                      aria-hidden="true"
                    />
                  ) : null}
                </button>
              );
            })}
          </div>
        </div>

        {isPending ? (
          <div
            className={`flex flex-1 items-center justify-center gap-2 py-16 text-sm font-medium ${NEU_TEXT_MUTED}`}
          >
            <Spinner size="sm" />
            {t("loading")}
          </div>
        ) : isError ? (
          <NeuPanel
            tone="pressed"
            className="flex flex-col items-center gap-4 p-8 text-center"
          >
            <p className={`text-sm font-medium ${NEU_TEXT_MUTED}`}>{t("errorLoad")}</p>
            <NeuButton onClick={() => refetch()}>{t("retry")}</NeuButton>
          </NeuPanel>
        ) : rooms.length === 0 ? (
          <NeuPanel
            tone="pressed"
            className="flex flex-1 flex-col items-center justify-center gap-4 p-8 text-center md:p-12"
          >
            <h2 className={`text-lg font-bold tracking-tight ${NEU_TEXT}`}>
              {searching ? t("noResults") : t("empty")}
            </h2>
            <p className={`max-w-md text-sm leading-relaxed ${NEU_TEXT_MUTED}`}>
              {searching ? t("noResultsHint", { query: debouncedKeyword }) : t("emptyHint")}
            </p>
            {searching ? (
              <NeuButton onClick={() => setKeyword("")}>{t("clearSearch")}</NeuButton>
            ) : null}
          </NeuPanel>
        ) : (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 md:gap-6 xl:grid-cols-3">
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
          variant="neu"
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