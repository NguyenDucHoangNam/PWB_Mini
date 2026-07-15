"use client";

import Link from "next/link";
import { memo } from "react";
import { useDropdownMenu, type DropdownItem } from "@/hooks/use-dropdown-menu";
import type { AuthUser } from "@/features/auth/stores/use-auth-store";

interface UserDropdownProps {
  user: AuthUser | null;
  labels: {
    logout: string;
    account: string;
  };
  items: DropdownItem[];
}

function UserDropdownImpl({ user, labels, items }: UserDropdownProps) {
  const { isOpen, focusedIndex, triggerRef, containerRef, onKeyDown, toggle, close } =
    useDropdownMenu(items);

  const initials = user?.email ? user.email.charAt(0).toUpperCase() : "?";

  return (
    <div className="relative" ref={containerRef} onKeyDown={onKeyDown}>
      <button
        ref={triggerRef}
        onClick={toggle}
        type="button"
        className="flex size-9 items-center justify-center rounded-full bg-neutral-100 border border-neutral-200 text-sm font-bold text-neutral-800 hover:bg-neutral-200 dark:bg-neutral-800 dark:border-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-700 focus:outline-none focus:ring-2 focus:ring-black dark:focus:ring-white focus:ring-offset-2"
        aria-label="User menu"
        aria-haspopup="menu"
        aria-expanded={isOpen}
      >
        {initials}
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-52 rounded-lg border border-neutral-200 bg-white py-1 shadow-lg dark:border-neutral-800 dark:bg-neutral-950 animate-in fade-in slide-in-from-top-2 duration-100"
        >
          <div className="px-4 py-2 border-b border-neutral-100 dark:border-neutral-800">
            <p className="truncate text-xs text-neutral-500 mt-0.5">{user?.email || labels.account}</p>
          </div>

          {items.map((item, idx) => {
            const isFocused = idx === focusedIndex;
            if (item.href) {
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  role="menuitem"
                  className={`block px-4 py-2 text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none ${
                    isFocused ? "bg-neutral-100 dark:bg-neutral-800" : ""
                  }`}
                  onClick={() => {
                    item.onSelect?.();
                    close();
                  }}
                >
                  {item.label}
                </Link>
              );
            }
            return (
              <button
                key={item.label}
                onClick={() => {
                  item.onSelect?.();
                  close();
                }}
                type="button"
                role="menuitem"
                className={`w-full text-left block px-4 py-2 text-sm text-black dark:text-white hover:bg-neutral-100 dark:hover:bg-neutral-800 border-t border-neutral-100 dark:border-neutral-800 font-semibold focus:outline-none ${
                  isFocused ? "bg-neutral-100 dark:bg-neutral-800" : ""
                }`}
              >
                {item.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

export const UserDropdown = memo(UserDropdownImpl);
