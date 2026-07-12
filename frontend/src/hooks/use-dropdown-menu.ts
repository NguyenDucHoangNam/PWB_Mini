"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export interface DropdownItem {
  label: string;
  href?: string;
  onSelect?: () => void;
}

export function useDropdownMenu(items: DropdownItem[]) {
  const [isOpen, setIsOpen] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    const handler = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [isOpen]);

  const open = useCallback(() => {
    setFocusedIndex(-1);
    setIsOpen(true);
  }, []);

  const toggle = useCallback(() => {
    setFocusedIndex(-1);
    setIsOpen((prev) => !prev);
  }, []);

  const close = useCallback(() => {
    setIsOpen(false);
    setFocusedIndex(-1);
  }, []);

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (!isOpen) return;
      if (event.key === "Escape") {
        setIsOpen(false);
        triggerRef.current?.focus();
      } else if (event.key === "ArrowDown") {
        event.preventDefault();
        setFocusedIndex((prev) => (prev + 1) % items.length);
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        setFocusedIndex((prev) => (prev - 1 + items.length) % items.length);
      } else if (event.key === "Enter") {
        event.preventDefault();
        const active = items[focusedIndex];
        if (active) {
          active.onSelect?.();
          setIsOpen(false);
        }
      }
    },
    [focusedIndex, isOpen, items],
  );

  return {
    isOpen,
    focusedIndex,
    triggerRef,
    containerRef,
    onKeyDown,
    open,
    toggle,
    close,
  };
}
