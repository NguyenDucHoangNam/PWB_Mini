"use client";

import type { ComponentType, ReactNode } from "react";
import { useTranslations } from "next-intl";
import { TriangleAlert } from "lucide-react";
import { Pagination } from "@/components/ui/pagination";
import {
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
  NeuPanel,
  NeuSkeleton,
} from "@/components/ui/neu";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";

interface LibraryPanelProps {
  children: ReactNode;
}

/**
 * The well that library content sits in — sunken, so the rows and cards inside it
 * are the things that read as raised. No overflow-hidden: it would clip the soft
 * shadows of the tiles at the edges.
 */
export function LibraryPanel({ children }: LibraryPanelProps) {
  return (
    <NeuPanel tone="pressed" className="flex flex-1 flex-col p-3 sm:p-4">
      {children}
    </NeuPanel>
  );
}

interface LibraryErrorStateProps {
  message: string;
  retryLabel: string;
  onRetry: () => void;
}

export function LibraryErrorState({ message, retryLabel, onRetry }: LibraryErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-1 flex-col items-center justify-center gap-5 px-6 py-12 text-center sm:py-16"
    >
      <span className="neu-raised grid size-14 place-items-center rounded-full border-none">
        <TriangleAlert className="size-6 text-rose-600 dark:text-rose-400" aria-hidden="true" />
      </span>
      <p className={`max-w-sm text-sm font-medium ${NEU_TEXT}`}>{message}</p>
      <NeuButton onClick={onRetry}>{retryLabel}</NeuButton>
    </div>
  );
}

interface LibraryEmptyStateProps {
  icon: ComponentType<{ className?: string; "aria-hidden"?: boolean }>;
  title: string;
  hint: string;
  action?: ReactNode;
}

export function LibraryEmptyState({ icon: Icon, title, hint, action }: LibraryEmptyStateProps) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 py-12 text-center sm:py-16">
      <span className="neu-raised grid size-16 place-items-center rounded-full border-none">
        <Icon className="size-6 text-indigo-600 dark:text-indigo-400" aria-hidden />
      </span>
      <div className="flex max-w-sm flex-col gap-1.5">
        <h3 className={`text-base font-bold tracking-tight ${NEU_TEXT}`}>{title}</h3>
        <p className={`text-sm leading-relaxed ${NEU_TEXT_MUTED}`}>{hint}</p>
      </div>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}

interface LibrarySkeletonProps {
  rows?: number;
}

export function LibraryRowsSkeleton({ rows = 6 }: LibrarySkeletonProps) {
  const t = useTranslations("voice.list");

  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3">
      <span className="sr-only">{t("loading")}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <div
          key={index}
          className="neu-raised-sm flex items-center gap-4 rounded-2xl border-none px-4 py-3.5"
        >
          <NeuSkeleton className="size-12 shrink-0 rounded-2xl" />
          <div className="flex min-w-0 flex-1 flex-col gap-2.5">
            <NeuSkeleton
              className="h-3.5 rounded-full"
              style={{ width: `${45 + ((index * 13) % 35)}%` }}
            />
            <NeuSkeleton className="h-3 w-24 rounded-full" />
          </div>
          <NeuSkeleton className="h-3 w-10 shrink-0 rounded-full" />
        </div>
      ))}
    </div>
  );
}

export function LibraryCardsSkeleton({ rows = 6 }: LibrarySkeletonProps) {
  const t = useTranslations("voice.list");

  return (
    <div
      role="status"
      aria-live="polite"
      className="grid gap-5 sm:grid-cols-2 sm:gap-6 xl:grid-cols-3"
    >
      <span className="sr-only">{t("loading")}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <NeuPanel key={index} className="flex flex-col gap-5 p-5">
          <div className="flex items-center gap-3">
            <NeuSkeleton
              className="h-4 rounded-full"
              style={{ width: `${40 + ((index * 17) % 30)}%` }}
            />
            <NeuSkeleton className="h-4 w-12 rounded-full" />
          </div>
          <NeuSkeleton className="h-12 rounded-2xl" />
        </NeuPanel>
      ))}
    </div>
  );
}

interface LibraryPaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function LibraryPagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: LibraryPaginationProps) {
  return (
    <Pagination
      variant="neu"
      page={page}
      totalPages={totalPages}
      totalElements={totalElements}
      pageSize={DEFAULT_PAGE_SIZE}
      onPageChange={onPageChange}
    />
  );
}
