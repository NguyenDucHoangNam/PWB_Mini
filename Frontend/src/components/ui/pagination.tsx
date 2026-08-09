"use client";

import { useTranslations } from "next-intl";
import { ChevronLeft, ChevronRight, MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { NEU_TEXT_MUTED, neuButton } from "@/components/ui/neu";
import { cn } from "@/lib/utils";

const DOTS = "dots" as const;

type RangeItem = number | typeof DOTS;

function paginationRange(current: number, total: number, siblingCount: number): RangeItem[] {
  const totalPageNumbers = siblingCount * 2 + 5;

  if (totalPageNumbers >= total) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  const leftSibling = Math.max(current - siblingCount, 1);
  const rightSibling = Math.min(current + siblingCount, total);
  const showLeftDots = leftSibling > 2;
  const showRightDots = rightSibling < total - 1;

  if (!showLeftDots && showRightDots) {
    const leftCount = 3 + 2 * siblingCount;
    return [...Array.from({ length: leftCount }, (_, i) => i + 1), DOTS, total];
  }

  if (showLeftDots && !showRightDots) {
    const rightCount = 3 + 2 * siblingCount;
    return [1, DOTS, ...Array.from({ length: rightCount }, (_, i) => total - rightCount + 1 + i)];
  }

  return [
    1,
    DOTS,
    ...Array.from({ length: rightSibling - leftSibling + 1 }, (_, i) => leftSibling + i),
    DOTS,
    total,
  ];
}

interface PaginationProps {
  /** Zero-indexed current page. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /** Provide with pageSize to render the "Showing x–y of z" summary. */
  totalElements?: number;
  pageSize?: number;
  siblingCount?: number;
  className?: string;
  /** `neu` swaps the bordered controls for soft-UI ones on a matte surface. */
  variant?: "default" | "neu";
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  totalElements,
  pageSize,
  siblingCount = 1,
  className,
  variant = "default",
}: PaginationProps) {
  const t = useTranslations("common.pagination");

  if (totalPages <= 1) return null;

  const current = page + 1;
  const range = paginationRange(current, totalPages, siblingCount);

  const goTo = (target: number) => {
    const clamped = Math.min(Math.max(target, 1), totalPages);
    if (clamped - 1 !== page) onPageChange(clamped - 1);
  };

  const showSummary =
    totalElements !== undefined && pageSize !== undefined && totalElements > 0;
  const from = page * (pageSize ?? 0) + 1;
  const to = Math.min(from + (pageSize ?? 0) - 1, totalElements ?? 0);

  if (variant === "neu") {
    return (
      <nav aria-label={t("label")} className={cn("flex flex-col items-center gap-3", className)}>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={neuButton({ size: "icon-sm" })}
            disabled={page === 0}
            onClick={() => goTo(current - 1)}
            aria-label={t("prev")}
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
          </button>

          {range.map((item, index) =>
            item === DOTS ? (
              <span
                key={`dots-${index}`}
                className={cn("grid size-9 place-items-center", NEU_TEXT_MUTED)}
                aria-label={t("morePages")}
              >
                <MoreHorizontal className="size-4" aria-hidden="true" />
              </span>
            ) : (
              <button
                key={item}
                type="button"
                // aria-current plus accent colour: the raised shadow alone would not
                // meet the non-text contrast bar for "this is the page you are on".
                aria-current={item === current ? "page" : undefined}
                aria-label={t("goToPage", { page: item })}
                onClick={() => goTo(item)}
                className={neuButton(
                  { variant: item === current ? "default" : "ghost", size: "icon-sm" },
                  cn(
                    "tabular-nums",
                    item === current && "text-indigo-600 dark:text-indigo-400",
                  ),
                )}
              >
                {item}
              </button>
            ),
          )}

          <button
            type="button"
            className={neuButton({ size: "icon-sm" })}
            disabled={page + 1 >= totalPages}
            onClick={() => goTo(current + 1)}
            aria-label={t("next")}
          >
            <ChevronRight className="size-4" aria-hidden="true" />
          </button>
        </div>

        {showSummary ? (
          <p className={cn("text-xs font-medium tabular-nums", NEU_TEXT_MUTED)}>
            {t("showing", { from, to, total: totalElements })}
          </p>
        ) : null}
      </nav>
    );
  }

  return (
    <nav aria-label={t("label")} className={cn("flex flex-col items-center gap-2.5", className)}>
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          className="size-9"
          disabled={page === 0}
          onClick={() => goTo(current - 1)}
          aria-label={t("prev")}
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
        </Button>

        {range.map((item, index) =>
          item === DOTS ? (
            <span
              key={`dots-${index}`}
              className="grid size-9 place-items-center text-muted-foreground"
              aria-label={t("morePages")}
            >
              <MoreHorizontal className="size-4" aria-hidden="true" />
            </span>
          ) : (
            <Button
              key={item}
              variant={item === current ? "default" : "ghost"}
              size="icon"
              className="size-9 tabular-nums"
              aria-current={item === current ? "page" : undefined}
              aria-label={t("goToPage", { page: item })}
              onClick={() => goTo(item)}
            >
              {item}
            </Button>
          ),
        )}

        <Button
          variant="outline"
          size="icon"
          className="size-9"
          disabled={page + 1 >= totalPages}
          onClick={() => goTo(current + 1)}
          aria-label={t("next")}
        >
          <ChevronRight className="size-4" aria-hidden="true" />
        </Button>
      </div>

      {showSummary ? (
        <p className="text-xs tabular-nums text-muted-foreground">
          {t("showing", { from, to, total: totalElements })}
        </p>
      ) : null}
    </nav>
  );
}
