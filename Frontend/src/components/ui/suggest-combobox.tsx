"use client";

import * as React from "react";
import { SearchInput } from "@/components/ui/search-input";
import { cn } from "@/lib/utils";

interface SuggestComboboxProps<T> {
  id: string;
  value: string;
  onValueChange: (value: string) => void;
  items: T[];
  getKey: (item: T) => string;
  renderItem: (item: T) => React.ReactNode;
  onSelect: (item: T) => void;
  loading?: boolean;
  placeholder?: string;
  emptyLabel?: string;
  clearLabel?: string;
  className?: string;
}

/**
 * A text field with a suggestion list under it.
 *
 * Keyboard handling is the point: arrows move the highlight, Enter takes it, Escape closes the list
 * without clearing the text. The highlight lives in `aria-activedescendant` rather than in DOM focus so
 * the caret stays in the input while the user walks the list — moving focus to the option would break
 * typing, which is the whole interaction here.
 */
export function SuggestCombobox<T>({
  id,
  value,
  onValueChange,
  items,
  getKey,
  renderItem,
  onSelect,
  loading = false,
  placeholder,
  emptyLabel,
  clearLabel,
  className,
}: SuggestComboboxProps<T>) {
  const [open, setOpen] = React.useState(false);
  const [highlighted, setHighlighted] = React.useState(0);
  const [renderedItems, setRenderedItems] = React.useState(items);
  const containerRef = React.useRef<HTMLDivElement>(null);

  const listId = `${id}-listbox`;
  const showList = open && value.trim().length > 0;

  // A fresh result set means the old highlight points at a row that is no longer there, so it goes back
  // to the top. Adjusted during render rather than in an effect: an effect would paint the stale
  // highlight first and then move it, which is visible as a flicker on every keystroke.
  if (renderedItems !== items) {
    setRenderedItems(items);
    setHighlighted(0);
  }

  // A click anywhere else means the user moved on; the list must not stay hanging over the page.
  React.useEffect(() => {
    if (!showList) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [showList]);

  const choose = (item: T) => {
    onSelect(item);
    setOpen(false);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (!showList || items.length === 0) return;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setHighlighted((current) => (current + 1) % items.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setHighlighted((current) => (current - 1 + items.length) % items.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      choose(items[highlighted]);
    }
  };

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <SearchInput
        id={id}
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={
          showList && items.length > 0 ? `${id}-option-${highlighted}` : undefined
        }
        autoComplete="off"
        placeholder={placeholder}
        clearLabel={clearLabel}
        loading={loading}
        value={value}
        onValueChange={(next) => {
          onValueChange(next);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
      />

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-50 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-neutral-200 bg-white py-1 shadow-lg dark:border-neutral-800 dark:bg-neutral-950"
        >
          {items.length === 0 ? (
            <li className="px-3 py-2 text-xs text-neutral-500 dark:text-neutral-400">
              {loading ? null : emptyLabel}
            </li>
          ) : (
            items.map((item, index) => (
              <li
                key={getKey(item)}
                id={`${id}-option-${index}`}
                role="option"
                aria-selected={index === highlighted}
                onMouseEnter={() => setHighlighted(index)}
                // pointerdown, not click: the input blurs first on a click and the list would already
                // be gone by the time the handler ran.
                onPointerDown={(event) => {
                  event.preventDefault();
                  choose(item);
                }}
                className={cn(
                  "cursor-pointer px-3 py-2 text-sm",
                  index === highlighted
                    ? "bg-neutral-100 dark:bg-neutral-900"
                    : "bg-transparent",
                )}
              >
                {renderItem(item)}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
