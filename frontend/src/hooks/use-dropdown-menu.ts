"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

export type DropdownItemVariant = "default" | "destructive";

export interface DropdownItem {
  label: string;
  href?: string;
  onSelect?: () => void;
  icon?: ReactNode;
  variant?: DropdownItemVariant;
  disabled?: boolean;
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
        const selectable = items
          .map((item, idx) => ({ item, idx }))
          .filter(({ item }) => !item.disabled);
        if (selectable.length === 0) return;
        const currentSelectableIdx = selectable.findIndex(({ idx }) => idx === focusedIndex);
        const next = selectable[(currentSelectableIdx + 1) % selectable.length];
        setFocusedIndex(next.idx);
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        const selectable = items
          .map((item, idx) => ({ item, idx }))
          .filter(({ item }) => !item.disabled);
        if (selectable.length === 0) return;
        const currentSelectableIdx = selectable.findIndex(({ idx }) => idx === focusedIndex);
        const next = selectable[(currentSelectableIdx - 1 + selectable.length) % selectable.length];
        setFocusedIndex(next.idx);
      } else if (event.key === "Enter") {
        event.preventDefault();
        const active = items[focusedIndex];
        if (active && !active.disabled) {
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
