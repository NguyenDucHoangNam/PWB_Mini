"use client";

import * as React from "react";
import { Search, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

interface SearchInputProps extends Omit<React.ComponentProps<"input">, "onChange" | "value"> {
  value: string;
  onValueChange: (value: string) => void;
  /** Shows a spinner in place of the clear button while a request is in flight. */
  loading?: boolean;
  clearLabel?: string;
}

/**
 * A text field with a magnifier, a clear button and room for a loading indicator.
 *
 * `type="search"` rather than `type="text"`: it gives the on-screen keyboard a search key on mobile and
 * lets Escape clear the field natively. The browser's own clear widget is suppressed in the stylesheet
 * so there are not two of them.
 */
export function SearchInput({
  value,
  onValueChange,
  loading = false,
  clearLabel = "Clear",
  className,
  ...props
}: SearchInputProps) {
  return (
    <div className="relative w-full">
      <Search
        aria-hidden
        className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-neutral-400"
      />
      <Input
        {...props}
        type="search"
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        className={cn(
          "h-9 pl-8 pr-9 [&::-webkit-search-cancel-button]:appearance-none",
          className,
        )}
      />
      <div className="absolute right-2 top-1/2 flex -translate-y-1/2 items-center">
        {loading ? (
          <Spinner size="sm" />
        ) : value ? (
          <button
            type="button"
            aria-label={clearLabel}
            onClick={() => onValueChange("")}
            className="rounded-full p-0.5 text-neutral-400 transition-colors hover:text-neutral-700 dark:hover:text-neutral-200"
          >
            <X className="size-4" />
          </button>
        ) : null}
      </div>
    </div>
  );
}
