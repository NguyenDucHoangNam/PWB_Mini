"use client";

import * as React from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import { NEU_FOCUS } from "@/components/ui/neu";
import { cn } from "@/lib/utils";

interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps {
  options: SelectOption[];
  value: string;
  onValueChange: (value: string) => void;
  className?: string;
  "aria-label"?: string;
}

export function Select({
  options,
  value,
  onValueChange,
  className,
  "aria-label": ariaLabel,
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const selectedOption = options.find((o) => o.value === value);

  const close = useCallback(() => {
    setOpen(false);
    setHighlightedIndex(-1);
  }, []);

  useEffect(() => {
    if (!open) return;

    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        close();
      }
    };

    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open, close]);

  useEffect(() => {
    if (open && listRef.current && highlightedIndex >= 0) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement | undefined;
      item?.scrollIntoView({ block: "nearest" });
    }
  }, [open, highlightedIndex]);

  const toggle = () => {
    if (open) {
      close();
    } else {
      const currentIndex = options.findIndex((o) => o.value === value);
      setHighlightedIndex(currentIndex >= 0 ? currentIndex : 0);
      setOpen(true);
    }
  };

  const select = (option: SelectOption) => {
    onValueChange(option.value);
    close();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        toggle();
      }
      return;
    }

    switch (e.key) {
      case "ArrowDown": {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev + 1) % options.length);
        break;
      }
      case "ArrowUp": {
        e.preventDefault();
        setHighlightedIndex((prev) => (prev - 1 + options.length) % options.length);
        break;
      }
      case "Enter":
      case " ": {
        e.preventDefault();
        if (highlightedIndex >= 0 && highlightedIndex < options.length) {
          select(options[highlightedIndex]);
        }
        break;
      }
      case "Home": {
        e.preventDefault();
        setHighlightedIndex(0);
        break;
      }
      case "End": {
        e.preventDefault();
        setHighlightedIndex(options.length - 1);
        break;
      }
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={ariaLabel}
        onClick={toggle}
        onKeyDown={handleKeyDown}
        className={cn(
          "flex h-11 w-full cursor-pointer items-center justify-between gap-2 rounded-2xl border-none px-4 text-sm font-semibold outline-none",
          NEU_FOCUS,
          // Open reads as pressed-in. The chevron flip and the accent text carry the
          // state for anyone who cannot see the shadow flip with it.
          open
            ? "neu-pressed text-indigo-600 dark:text-indigo-400"
            : "neu-button text-slate-700 dark:text-slate-200",
          className,
        )}
      >
        <span className="truncate">{selectedOption?.label}</span>
        <ChevronDown
          aria-hidden="true"
          className={cn(
            "size-4 shrink-0 transition-transform duration-200 motion-reduce:transition-none",
            open ? "rotate-180" : "text-slate-400 dark:text-slate-500",
          )}
        />
      </button>

      {open && (
        <ul
          ref={listRef}
          role="listbox"
          aria-label={ariaLabel}
          className="neu-raised absolute left-0 top-[calc(100%+10px)] z-50 w-full min-w-[8rem] rounded-2xl border-none p-2 animate-in fade-in-0 zoom-in-95 slide-in-from-top-2 motion-reduce:animate-none"
        >
          {options.map((option, index) => {
            const isSelected = option.value === value;
            const isHighlighted = index === highlightedIndex;
            return (
              <li
                key={option.value}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setHighlightedIndex(index)}
                onMouseDown={(e) => {
                  e.preventDefault();
                  select(option);
                }}
                className={cn(
                  "flex cursor-pointer items-center gap-2 rounded-xl border-none px-3 py-2 text-sm outline-none transition-all",
                  isHighlighted && "neu-pressed-sm",
                  isSelected
                    ? "font-bold text-indigo-600 dark:text-indigo-400"
                    : "font-medium text-slate-600 dark:text-slate-300",
                )}
              >
                <Check
                  aria-hidden="true"
                  className={cn(
                    "size-3.5 shrink-0 transition-opacity",
                    isSelected ? "opacity-100" : "opacity-0",
                  )}
                />
                <span className="truncate">{option.label}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
