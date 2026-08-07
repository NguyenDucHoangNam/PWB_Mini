"use client";

import type { ComponentType, ReactNode } from "react";
import { useTranslations } from "next-intl";
import { TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";

interface LibraryPanelProps {
  children: ReactNode;
}

export function LibraryPanel({ children }: LibraryPanelProps) {
  return (
    <div className="flex flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">{children}</div>
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
      className="flex flex-1 flex-col items-center justify-center gap-4 px-6 py-12 text-center sm:py-16"
    >
      <TriangleAlert className="size-6 text-destructive" aria-hidden="true" />
      <p className="max-w-sm text-sm text-foreground">{message}</p>
      <Button variant="outline" size="lg" onClick={onRetry}>
        {retryLabel}
      </Button>
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
    <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-12 text-center sm:py-16">
      <Icon className="size-6 text-muted-foreground" aria-hidden />
      <div className="flex max-w-sm flex-col gap-1.5">
        <h3 className="text-base font-semibold tracking-tight text-foreground">{title}</h3>
        <p className="text-sm leading-relaxed text-muted-foreground">{hint}</p>
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
    <div role="status" aria-live="polite" className="divide-y divide-border">
      <span className="sr-only">{t("loading")}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-3 px-3 py-4 sm:gap-4 sm:px-4">
          <div className="hidden h-3 w-6 shrink-0 animate-pulse rounded bg-muted sm:block" />
          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <div
              className="h-3.5 animate-pulse rounded bg-muted"
              style={{ width: `${45 + ((index * 13) % 35)}%` }}
            />
            <div className="h-3 w-24 animate-pulse rounded bg-muted/60 sm:hidden" />
          </div>
          <div className="hidden h-3 w-12 shrink-0 animate-pulse rounded bg-muted/60 sm:block" />
          <div className="h-3 w-10 shrink-0 animate-pulse rounded bg-muted/60" />
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
      className="grid gap-3 sm:grid-cols-2 sm:gap-4 xl:grid-cols-3"
    >
      <span className="sr-only">{t("loading")}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4">
          <div className="flex items-center gap-2">
            <div
              className="h-4 animate-pulse rounded bg-muted"
              style={{ width: `${40 + ((index * 17) % 30)}%` }}
            />
            <div className="h-4 w-12 animate-pulse rounded bg-muted/60" />
          </div>
          <div className="h-10 animate-pulse rounded-lg bg-muted/60" />
        </div>
      ))}
    </div>
  );
}

interface LibraryPaginationProps {
  page: number;
  totalPages: number;
  rangeFrom: number;
  rangeTo: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function LibraryPagination({
  page,
  totalPages,
  rangeFrom,
  rangeTo,
  totalElements,
  onPageChange,
}: LibraryPaginationProps) {
  const t = useTranslations("voice.list");

  if (totalPages <= 1) return null;

  return (
    <nav
      aria-label={t("showingRange", { from: rangeFrom, to: rangeTo, total: totalElements })}
      className="flex flex-col-reverse items-center gap-3 sm:flex-row sm:justify-between"
    >
      <p className="text-xs text-muted-foreground">
        {t("showingRange", { from: rangeFrom, to: rangeTo, total: totalElements })}
      </p>
      <div className="flex w-full items-center justify-between gap-3 sm:w-auto sm:justify-end">
        <Button
          variant="outline"
          size="lg"
          className="min-w-20 sm:h-8"
          disabled={page === 0}
          onClick={() => onPageChange(Math.max(0, page - 1))}
        >
          {t("prev")}
        </Button>
        <span className="text-xs tabular-nums text-muted-foreground">
          {page + 1} / {totalPages}
        </span>
        <Button
          variant="outline"
          size="lg"
          className="min-w-20 sm:h-8"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          {t("next")}
        </Button>
      </div>
    </nav>
  );
}
